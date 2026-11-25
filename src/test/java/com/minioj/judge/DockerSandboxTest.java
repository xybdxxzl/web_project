package com.minioj.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DockerSandboxTest {

    private static final String IMAGE = new JavaLanguage().dockerImage();
    private static final ProcessExecutor PROCESSES = new ProcessExecutor();

    @Test
    void buildsIsolatedCompileAndRunCommands() {
        var sandbox = new DockerSandbox(1000);
        var language = new JavaLanguage();
        var compile = sandbox.createCommand(language, Path.of("work"), "mini-oj-1-compile-test",
                true, language.compileCommand(), 32, 10000);
        var run = sandbox.createCommand(language, Path.of("work"), "mini-oj-1-case-1-test",
                false, language.runCommand(32), 32, 2000);

        assertThat(compile).contains("--rm", "--read-only", "--pull=never", "--cap-drop", "ALL",
                "--network", "none", "--pids-limit", "64", "--cpus", "1.0",
                "--user", "nobody", "--security-opt", "no-new-privileges", "--log-driver", "none",
                "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=64m")
                .containsSubsequence("--memory", "160m", "--memory-swap", "160m")
                .containsSequence("--entrypoint", "/usr/bin/timeout")
                .containsSequence(IMAGE, "--signal=KILL", "15.000s")
                .endsWith("javac", "-encoding", "UTF-8", "-proc:none", "-classpath", ".", "Main.java");
        assertThat(compile.get(compile.indexOf("--mount") + 1)).endsWith("target=/workspace");
        assertThat(run.get(run.indexOf("--mount") + 1)).endsWith("target=/workspace,readonly");
        assertThat(run).containsSequence("java", "-Dfile.encoding=UTF-8", "-Xmx32m");
        assertThat(run).containsSequence(IMAGE, "--signal=KILL", "7.000s");
        assertThat(sandbox.isMemoryLimitExceeded(language, new Sandbox.Result(false, false, 137, 23, "", ""))).isTrue();
    }

    @Test
    void usesGccImageAndExactMemoryLimitForCppRuntime() {
        var sandbox = new DockerSandbox(1000);
        var language = new CppLanguage();
        var compile = sandbox.createCommand(language, Path.of("work"), "compile", true,
                language.compileCommand(), 32, 10000);
        var run = sandbox.createCommand(language, Path.of("work"), "run", false,
                language.runCommand(32), 32, 2000);

        assertThat(compile).contains("gcc:13").containsSequence("--memory", "256m")
                .endsWith("g++", "-O2", "-std=c++17", "-o", "main", "main.cpp");
        assertThat(run).contains("gcc:13").containsSequence("--memory", "32m").endsWith("./main");
        assertThat(sandbox.isMemoryLimitExceeded(language, new Sandbox.Result(false, false, 137, 1, "", ""))).isTrue();
        assertThat(sandbox.isMemoryLimitExceeded(language, new Sandbox.Result(false, false, 134, 1, "", "bad_alloc"))).isFalse();
        assertThat(sandbox.isMemoryLimitExceeded(language, new Sandbox.Result(false, false, 139, 1, "", ""))).isFalse();
    }

    @org.junit.jupiter.api.Nested
    class WithDocker {

        private final DockerSandbox sandbox = new DockerSandbox(1000);
        private final JavaLanguage language = new JavaLanguage();

        @BeforeAll
        static void requireDocker() throws Exception {
            assumeTrue(isDockerAvailable());
            var image = docker(5000, "image", "inspect", "--format", "{{.Id}}", IMAGE);
            if (image.exitCode() != 0) {
                var pulled = docker(180000, "pull", IMAGE);
                assertThat(pulled.timedOut()).as("Docker image pull timed out").isFalse();
                assertThat(pulled.exitCode()).as(pulled.stderr()).isZero();
            }
        }

        @Test
        void timesOutAndRemovesTheContainer(@TempDir Path directory) throws Exception {
            var workspace = compile(directory, 601, """
                    System.out.println("started");
                    while (true) {}
                    """);

            var result = sandbox.run(language, workspace, 1, "", 2000, 32);

            assertThat(result.timedOut()).isTrue();
            assertThat(result.stdout()).contains("started");
            var remaining = docker(5000, "ps", "-aq", "--filter", "name=mini-oj-601-case-1-");
            assertThat(remaining.exitCode()).as(remaining.stderr()).isZero();
            assertThat(remaining.stdout()).isBlank();
        }

        @Test
        void watchdogRemovesRunningContainerWithoutAnAttachedClient(@TempDir Path directory) throws Exception {
            var workspace = compile(directory, 604, "while (true) {}");
            String name = "mini-oj-604-watchdog-" + UUID.randomUUID();
            var args = sandbox.createCommand(language, workspace.directory(), name, false, language.runCommand(32), 32, 1000);
            try {
                var created = PROCESSES.execute(new ProcessBuilder(args), "", 5000);
                assertThat(created.exitCode()).as(created.stderr()).isZero();
                var started = docker(5000, "start", name);
                assertThat(started.exitCode()).as(started.stderr()).isZero();
                var state = docker(5000, "inspect", "--format", "{{.State.Running}}", name);
                assertThat(state.exitCode()).as(state.stderr()).isZero();
                assertThat(state.stdout().strip()).isEqualTo("true");

                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                do {
                    var remaining = docker(5000, "ps", "-aq", "--filter", "name=" + name);
                    assertThat(remaining.exitCode()).as(remaining.stderr()).isZero();
                    if (remaining.stdout().isBlank()) {
                        return;
                    }
                    Thread.sleep(200);
                } while (System.nanoTime() < deadline);
                throw new AssertionError("容器在兜底期限后仍存在");
            } finally {
                docker(5000, "rm", "-f", name);
            }
        }

        @Test
        void classifiesLargeArrayAsMemoryLimitExceeded(@TempDir Path directory) throws Exception {
            var workspace = compile(directory, 602, """
                    var array = new byte[128 * 1024 * 1024];
                    System.out.println(array.length);
                    """);

            var result = sandbox.run(language, workspace, 1, "", 5000, 32);

            assertThat(result.timedOut()).isFalse();
            assertThat(result.exitCode()).isNotZero();
            assertThat(sandbox.isMemoryLimitExceeded(language, result)).as(result.stderr()).isTrue();
        }

        @Test
        void exposesOnlyLoopbackAndMountsWorkReadOnly(@TempDir Path directory) throws Exception {
            var workspace = compile(directory, 603, """
                    for (var nic : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                        if (!nic.isLoopback()) {
                            throw new IllegalStateException("non-loopback network interface");
                        }
                    }
                    try (var probe = new java.net.Socket()) {
                        probe.connect(new java.net.InetSocketAddress("192.0.2.1", 80), 400);
                        throw new IllegalStateException("unexpected outbound connection");
                    } catch (java.io.IOException expected) {
                        // TEST-NET-1 只用于探测容器内有没有外网路由。
                    }
                    try {
                        java.nio.file.Files.writeString(java.nio.file.Path.of("/workspace/write-check"), "x");
                        throw new IllegalStateException("workspace is writable");
                    } catch (java.io.IOException expected) {
                        System.out.println("isolated");
                    }
                    """);

            var result = sandbox.run(language, workspace, 1, "", 5000, 32);

            assertThat(result.timedOut()).isFalse();
            assertThat(result.exitCode()).as(result.stderr()).isZero();
            assertThat(result.stdout()).contains("isolated");
            assertThat(directory.resolve("write-check")).doesNotExist();
        }

        private Sandbox.Workspace compile(Path directory, long id, String body) throws Exception {
            Files.writeString(directory.resolve("Main.java"),
                    "public class Main { public static void main(String[] args) throws Exception { " + body + " } }");
            var workspace = new Sandbox.Workspace(id, directory);
            var result = sandbox.compile(language, workspace, 10000, 32);
            assertThat(result.timedOut()).isFalse();
            assertThat(result.exitCode()).as(result.stderr()).isZero();
            assertThat(directory.resolve("Main.class")).isRegularFile();
            return workspace;
        }
    }

    private static boolean isDockerAvailable() {
        try {
            var result = docker(5000, "info", "--format", "{{.OSType}}");
            return result.exitCode() == 0 && "linux".equals(result.stdout().strip());
        } catch (IOException unavailable) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Sandbox.Result docker(long timeoutMs, String... arguments) throws IOException, InterruptedException {
        var builder = new ProcessBuilder("docker");
        builder.command().addAll(List.of(arguments));
        return PROCESSES.execute(builder, "", timeoutMs);
    }
}
