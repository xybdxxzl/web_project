package com.minioj.judge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

final class ProcessExecutor {

    private static final int OUTPUT_LIMIT = 1024 * 1024;
    private static final long PROCESS_TREE_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(20);

    Sandbox.Result execute(ProcessBuilder builder, String input, long timeLimitMs)
            throws IOException, InterruptedException {
        long start = System.nanoTime();
        Process process = builder.start();
        Set<ProcessHandle> descendants = new HashSet<>();
        var remaining = new AtomicInteger(OUTPUT_LIMIT);
        var overflow = new CompletableFuture<Void>();
        var failure = new CompletableFuture<IOException>();
        var stdout = new Capture(process.getInputStream(), remaining, overflow, failure);
        var stderr = new Capture(process.getErrorStream(), remaining, overflow, failure);
        boolean timedOut;
        boolean exceeded;
        long elapsed;
        int exitCode;
        try {
            startIo(stdout, "stdout");
            startIo(stderr, "stderr");
            // 程序可能在读完输入前大量输出，写输入同样不能占用监控线程。
            startIo(() -> feedInput(process, input), "stdin");

            var completed = CompletableFuture.allOf(process.onExit(), stdout.done, stderr.done);
            var stop = CompletableFuture.anyOf(completed, overflow, failure).thenApply(ignored -> System.nanoTime());
            timedOut = await(process, descendants, stop, start + TimeUnit.MILLISECONDS.toNanos(timeLimitMs));
            elapsed = TimeUnit.NANOSECONDS.toMillis((timedOut ? System.nanoTime() : stop.join()) - start);
            exceeded = overflow.isDone();
            if (!timedOut && !exceeded && failure.isDone()) {
                throw failure.join();
            }
            exitCode = timedOut || exceeded ? -1 : process.exitValue();
        } finally {
            terminate(process, descendants);
        }
        return new Sandbox.Result(timedOut, exceeded, exitCode, elapsed, stdout.text(), stderr.text());
    }

    private boolean await(Process process, Set<ProcessHandle> descendants, CompletableFuture<Long> stop, long deadline)
            throws IOException, InterruptedException {
        try {
            while (!stop.isDone()) {
                process.descendants().forEach(descendants::add);
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return true;
                }
                try {
                    stop.get(Math.min(remaining, PROCESS_TREE_POLL_NANOS), TimeUnit.NANOSECONDS);
                } catch (TimeoutException ignored) {
                    // 周期性记录后代，避免父进程先退出后失去可清理的进程句柄。
                }
            }
            return stop.get() - deadline > 0;
        } catch (ExecutionException exception) {
            throw new IOException("无法监控判题进程", exception.getCause());
        }
    }

    private void terminate(Process process, Set<ProcessHandle> descendants) throws IOException, InterruptedException {
        process.descendants().forEach(descendants::add);
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            throw new IOException("判题进程未能退出");
        }
        try {
            CompletableFuture<?>[] exits = descendants.stream().filter(ProcessHandle::isAlive)
                    .map(ProcessHandle::onExit).toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(exits).get(2, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("判题子进程未能退出", exception);
        }
    }

    private void feedInput(Process process, String input) {
        try (var stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 提前退出或主动关闭 stdin 的程序可以合法地不读完输入。
        }
    }

    private void startIo(Runnable transfer, String stream) {
        Thread thread = new Thread(transfer, "oj-" + stream);
        thread.setDaemon(true);
        thread.start();
    }

    private static final class Capture implements Runnable {

        private final InputStream stream;
        private final AtomicInteger remaining;
        private final CompletableFuture<Void> overflow;
        private final CompletableFuture<IOException> failure;
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private Capture(InputStream stream, AtomicInteger remaining, CompletableFuture<Void> overflow,
                        CompletableFuture<IOException> failure) {
            this.stream = stream;
            this.remaining = remaining;
            this.overflow = overflow;
            this.failure = failure;
        }

        @Override
        public void run() {
            try (stream) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    int length = count;
                    int budget = remaining.getAndUpdate(available -> Math.max(0, available - length));
                    output.write(buffer, 0, Math.min(budget, length));
                    if (length > budget) {
                        overflow.complete(null);
                        return;
                    }
                }
            } catch (IOException exception) {
                failure.complete(exception);
            } finally {
                done.complete(null);
            }
        }

        private String text() {
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}
