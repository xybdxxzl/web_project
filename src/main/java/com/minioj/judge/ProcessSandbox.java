package com.minioj.judge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.minioj.entity.LanguageType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "oj.sandbox.type", havingValue = "process")
public class ProcessSandbox implements Sandbox {

    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private final ProcessExecutor processes = new ProcessExecutor();

    @Override
    public Result compile(Language language, Workspace workspace, int timeLimitMs, int memoryLimitMb)
            throws IOException, InterruptedException {
        return execute(language, language.compileCommand(), workspace.directory(), "", timeLimitMs);
    }

    @Override
    public Result run(Language language, Workspace workspace, int caseNumber, String input,
                      int timeLimitMs, int memoryLimitMb) throws IOException, InterruptedException {
        List<String> command = language.runCommand(memoryLimitMb);
        if (language.type() == LanguageType.CPP) {
            if (WINDOWS) {
                command = List.of(workspace.directory().resolve("main.exe").toString());
            } else {
                // 受信的包装命令只接受位置参数，exec 保留实际程序的退出状态。
                var limited = new ArrayList<>(List.of("/bin/sh", "-c", "ulimit -v \"$1\" || exit 125; shift; exec \"$@\"",
                        "mini-oj", Long.toString((long) memoryLimitMb * 1024)));
                limited.addAll(command);
                command = limited;
            }
        }
        return execute(language, command, workspace.directory(), input, timeLimitMs);
    }

    @Override
    public boolean isMemoryLimitExceeded(Language language, Result result) {
        return language.type() == LanguageType.CPP
                ? result.exitCode() != 139 && result.stderr().contains("bad_alloc")
                : Sandbox.super.isMemoryLimitExceeded(language, result);
    }

    private Result execute(Language language, List<String> command, Path directory, String input, int timeLimitMs)
            throws IOException, InterruptedException {
        List<String> resolved = new ArrayList<>(command);
        if (language.type() == LanguageType.JAVA) {
            String executable = command.get(0) + (WINDOWS ? ".exe" : "");
            resolved.set(0, Path.of(System.getProperty("java.home"), "bin", executable).toString());
        }
        ProcessBuilder builder = new ProcessBuilder(resolved).directory(directory.toFile());

        // 判题子进程无需继承 Web 应用的数据库和管理员凭据。
        String compilerPath = builder.environment().get("PATH");
        builder.environment().clear();
        if (language.type() == LanguageType.CPP && compilerPath != null) {
            // g++ 需要查找链接工具，Windows 下程序还需要 MinGW 的运行时 DLL。
            builder.environment().put("PATH", compilerPath);
        }
        String systemRoot = System.getenv("SystemRoot");
        if (WINDOWS && systemRoot != null) {
            builder.environment().put("SystemRoot", systemRoot);
        }
        builder.environment().put("TMP", directory.toString());
        builder.environment().put("TEMP", directory.toString());
        builder.environment().put("TMPDIR", directory.toString());
        return processes.execute(builder, input, timeLimitMs);
    }
}
