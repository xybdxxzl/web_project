package com.minioj.judge;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.minioj.entity.JudgeStatus;
import com.minioj.entity.LanguageType;
import com.minioj.entity.TestCase;
import com.minioj.repository.SubmissionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JudgeEngine {

    private static final List<JudgeStatus> ACTIVE = List.of(JudgeStatus.PENDING, JudgeStatus.JUDGING);
    private static final int ERROR_MESSAGE_LIMIT = 4096;
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    private final SubmissionRepository submissions;
    private final Sandbox sandbox;
    private final Map<LanguageType, Language> languages;
    private final ThreadPoolExecutor executor;
    private final int compileTimeoutMs;

    public JudgeEngine(SubmissionRepository submissions, Sandbox sandbox, List<Language> languages,
                       @Value("${oj.judge.workers}") int workers,
                       @Value("${oj.judge.queue-capacity}") int queueCapacity,
                       @Value("${oj.judge.compile-timeout-ms}") int compileTimeoutMs) {
        this.submissions = submissions;
        this.sandbox = sandbox;
        this.languages = languages.stream().collect(Collectors.toUnmodifiableMap(Language::type, Function.identity()));
        this.compileTimeoutMs = compileTimeoutMs;
        this.executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), (task, pool) -> ((Task) task).reject("判题队列暂不可用"));
    }

    @PostConstruct
    void recoverInterrupted() {
        submissions.finishInterrupted(ACTIVE, JudgeStatus.SE, "应用重启，判题任务已中断");
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow().forEach(task -> ((Task) task).reject("应用关闭，判题任务已中断"));
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public void enqueue(JudgeJob job) {
        executor.execute(new Task(job));
    }

    private void judge(JudgeJob job) {
        try {
            if (submissions.transition(job.submissionId(), JudgeStatus.PENDING, JudgeStatus.JUDGING) == 0) {
                return;
            }
            finish(job.submissionId(), execute(job));
        } catch (Throwable error) {
            log.error("提交 {} 判题异常", job.submissionId(), error);
            try {
                finish(job.submissionId(), new Verdict(JudgeStatus.SE, null, "判题系统错误：" + error.getClass().getSimpleName()));
            } finally {
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private Verdict execute(JudgeJob job) throws IOException, InterruptedException {
        Language language = languages.get(job.language());
        Path root = Files.createTempDirectory("mini-oj-" + job.submissionId() + "-");
        try {
            // Docker 只挂载子目录，宿主上的临时根目录保持私有。
            Path directory = Files.createDirectory(root.resolve("work"));
            var workspace = new Sandbox.Workspace(job.submissionId(), directory);
            Files.writeString(directory.resolve(language.sourceFileName()), job.code(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Sandbox.Result compilation = sandbox.compile(language, workspace, compileTimeoutMs, job.memoryLimitMb());
            if (compilation.timedOut() || compilation.outputLimitExceeded() || compilation.exitCode() != 0) {
                return new Verdict(JudgeStatus.CE, null, compileError(compilation));
            }

            long maximumTime = 0;
            int caseNumber = 0;
            for (TestCase testCase : job.testCases()) {
                Sandbox.Result result = sandbox.run(language, workspace, ++caseNumber, testCase.getInput(),
                        job.timeLimitMs(), job.memoryLimitMb());
                maximumTime = Math.max(maximumTime, result.timeUsedMs());
                Verdict verdict = evaluate(language, result, testCase.getExpectedOutput(), maximumTime);
                if (verdict.status() != JudgeStatus.AC) {
                    return verdict;
                }
            }
            return new Verdict(JudgeStatus.AC, maximumTime, null);
        } finally {
            deleteTree(root);
        }
    }

    private String compileError(Sandbox.Result result) {
        String message = result.timedOut() ? "编译超时"
                : result.outputLimitExceeded() ? "编译输出超过 1 MiB" : "编译失败";
        return result.stderr().isBlank() ? message : message + "\n" + result.stderr();
    }

    private Verdict evaluate(Language language, Sandbox.Result result, String expected, long maximumTime) {
        if (result.timedOut()) {
            return new Verdict(JudgeStatus.TLE, maximumTime, "运行超时");
        }
        if (result.outputLimitExceeded()) {
            return new Verdict(JudgeStatus.RE, maximumTime, "单次运行的输出超过 1 MiB");
        }
        if (result.exitCode() != 0) {
            if (sandbox.isMemoryLimitExceeded(language, result)) {
                return new Verdict(JudgeStatus.MLE, maximumTime, "运行内存超限");
            }
            return new Verdict(JudgeStatus.RE, maximumTime, "程序异常退出，退出码 " + result.exitCode());
        }
        if (!normalize(result.stdout()).equals(normalize(expected))) {
            return new Verdict(JudgeStatus.WA, maximumTime, "输出与预期不一致");
        }
        return new Verdict(JudgeStatus.AC, maximumTime, null);
    }

    private String normalize(String output) {
        String[] lines = Arrays.stream(output.replace("\r\n", "\n").split("\n", -1))
                .map(String::stripTrailing).toArray(String[]::new);
        int end = lines.length;
        while (end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        return String.join("\n", Arrays.copyOf(lines, end));
    }

    private void finish(Long id, Verdict verdict) {
        String message = verdict.message();
        if (message != null && message.length() > ERROR_MESSAGE_LIMIT) {
            message = message.substring(0, ERROR_MESSAGE_LIMIT - 1) + "…";
        }
        submissions.finish(id, ACTIVE, verdict.status(), verdict.elapsed(), message);
    }

    private void deleteTree(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                if (attributes.isOther()) {
                    // NTFS 目录联接可同时是目录与特殊文件，默认遍历仍会进入目标。
                    deleteEntry(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                deleteEntry(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                if (failure instanceof NoSuchFileException) {
                    return FileVisitResult.CONTINUE;
                }
                throw failure;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                deleteEntry(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteEntry(Path entry) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                Files.deleteIfExists(entry);
                return;
            } catch (FileSystemException error) {
                if (!WINDOWS || attempt == 10 || Thread.currentThread().isInterrupted()) {
                    throw error;
                }
                // Windows 可能在子进程退出后短时间内仍持有其工作目录。
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
            }
        }
    }

    private record Verdict(JudgeStatus status, Long elapsed, String message) {
    }

    private final class Task implements Runnable {

        private final JudgeJob job;

        private Task(JudgeJob job) {
            this.job = job;
        }

        @Override
        public void run() {
            judge(job);
        }

        private void reject(String message) {
            finish(job.submissionId(), new Verdict(JudgeStatus.SE, null, message));
        }
    }
}
