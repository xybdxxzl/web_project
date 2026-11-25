package com.minioj.judge;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.minioj.entity.LanguageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "oj.sandbox.type", havingValue = "docker", matchIfMissing = true)
public class DockerSandbox implements Sandbox {

    private static final int CLEANUP_TIMEOUT_MS = 5000;
    private static final String WORKDIR = "/workspace";
    private static final List<String> PROXY_VARIABLES = List.of(
            "HTTP_PROXY", "HTTPS_PROXY", "FTP_PROXY", "ALL_PROXY", "NO_PROXY",
            "http_proxy", "https_proxy", "ftp_proxy", "all_proxy", "no_proxy");

    private final ProcessExecutor processes = new ProcessExecutor();
    private final int startupMarginMs;

    public DockerSandbox(@Value("${oj.sandbox.docker.startup-margin-ms}") int startupMarginMs) {
        this.startupMarginMs = startupMarginMs;
    }

    @Override
    public Result compile(Language language, Workspace workspace, int timeLimitMs, int memoryLimitMb)
            throws IOException, InterruptedException {
        Path directory = workspace.directory();
        boolean posix = Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null;
        if (posix) {
            Files.setPosixFilePermissions(directory.resolve(language.sourceFileName()),
                    PosixFilePermissions.fromString("rw-r--r--"));
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxrwxrwx"));
        }
        try {
            return execute(language, workspace, "compile", true, language.compileCommand(), "", timeLimitMs, memoryLimitMb);
        } finally {
            if (posix) {
                // 运行阶段的 nobody 仍需读取编译产物，只有属主保留宿主目录写权限。
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
        }
    }

    @Override
    public Result run(Language language, Workspace workspace, int caseNumber, String input,
                      int timeLimitMs, int memoryLimitMb) throws IOException, InterruptedException {
        return execute(language, workspace, "case-" + caseNumber, false, language.runCommand(memoryLimitMb), input,
                (long) timeLimitMs + startupMarginMs, memoryLimitMb);
    }

    @Override
    public boolean isMemoryLimitExceeded(Language language, Result result) {
        return result.exitCode() == 137 || Sandbox.super.isMemoryLimitExceeded(language, result);
    }

    private Result execute(Language language, Workspace workspace, String stage, boolean writable, List<String> command,
                           String input, long timeLimitMs, int memoryLimitMb) throws IOException, InterruptedException {
        String name = "mini-oj-" + workspace.submissionId() + "-" + stage + "-" + UUID.randomUUID();
        long started = System.nanoTime();
        boolean creationUnconfirmed = true;
        try {
            // 先创建再启动，创建超时时，即使 Docker 守护进程仍在处理请求，也不会开始执行用户代码。
            Result created = docker(createCommand(language, workspace.directory(), name, writable, command, memoryLimitMb,
                    timeLimitMs), "", timeLimitMs);
            creationUnconfirmed = created.timedOut() || created.outputLimitExceeded();
            long creationMs = elapsed(started);
            if (created.timedOut() || creationMs >= timeLimitMs) {
                return new Result(true, false, -1, creationMs, "", created.stderr());
            }
            if (created.exitCode() != 0 || created.outputLimitExceeded()) {
                throw new IOException("无法创建判题容器 " + name + "：" + created.stderr());
            }

            Result result = docker(List.of("docker", "start", "--attach", "--interactive", name),
                    input, timeLimitMs - creationMs);
            long totalMs = creationMs + result.timeUsedMs();
            return new Result(result.timedOut() || totalMs > timeLimitMs, result.outputLimitExceeded(),
                    result.exitCode(), totalMs, result.stdout(), result.stderr());
        } finally {
            remove(name, creationUnconfirmed);
        }
    }

    List<String> createCommand(Language language, Path directory, String name, boolean writable, List<String> command, int memoryLimitMb,
                               long timeLimitMs) {
        // 编译器自身的内存开销与考生程序的运行限额无关。
        long containerMemory = language.type() == LanguageType.JAVA ? (long) memoryLimitMb + 128
                : writable ? Math.max(memoryLimitMb, 256) : memoryLimitMb;
        String memory = containerMemory + "m";
        String source = "source=" + directory.toAbsolutePath();
        if (source.contains(",") || source.contains("\"")) {
            source = "\"" + source.replace("\"", "\"\"") + "\"";
        }
        String mount = "type=bind," + source + ",target=" + WORKDIR + (writable ? "" : ",readonly");
        List<String> args = new ArrayList<>(List.of("docker", "create", "--rm", "--interactive",
                "--pull=never", "--name", name, "--network", "none", "--cpus", "1.0", "--pids-limit", "64",
                "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=64m", "--user", "nobody",
                "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
                "--memory", memory, "--memory-swap", memory, "--log-driver", "none",
                "--mount", mount, "--workdir", WORKDIR, "--env", "HOME=/tmp", "--entrypoint", "/usr/bin/timeout"));
        // Docker CLI 会从本地配置注入代理变量，其中可能含有认证信息。
        for (String proxy : PROXY_VARIABLES) {
            args.add("--env");
            args.add(proxy + "=");
        }
        args.add(language.dockerImage());
        // 宿主应用意外退出时仍有人终止任务。期限晚于宿主判题超时，正常情况下由宿主给出判定。
        args.add("--signal=KILL");
        args.add(BigDecimal.valueOf(timeLimitMs + CLEANUP_TIMEOUT_MS, 3).toPlainString() + "s");
        args.addAll(command);
        return args;
    }

    private void remove(String name, boolean creationUnconfirmed) throws IOException, InterruptedException {
        boolean interrupted = Thread.interrupted();
        try {
            for (int attempt = 0; ; attempt++) {
                try {
                    Result removed = docker(List.of("docker", "rm", "-f", name), "", CLEANUP_TIMEOUT_MS);
                    if (removed.exitCode() == 0) {
                        return;
                    }
                    String error = removed.stderr();
                    boolean missing = error.contains("No such container");
                    if (missing && (!creationUnconfirmed || attempt >= 3)) {
                        return;
                    }
                    if (attempt >= 3 || !(missing || error.contains("removal of container")
                            && error.contains("already in progress"))) {
                        throw new IOException("无法清理判题容器 " + name + "：" + error);
                    }
                    Thread.sleep(200);
                } catch (InterruptedException cancelled) {
                    if (interrupted) {
                        throw cancelled;
                    }
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Result docker(List<String> command, String input, long timeLimitMs)
            throws IOException, InterruptedException {
        return processes.execute(new ProcessBuilder(command), input, timeLimitMs);
    }

    private long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
