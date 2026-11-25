package com.minioj.judge;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import com.minioj.entity.JudgeStatus;
import com.minioj.entity.TestCase;
import com.minioj.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CppJudgeTest {

    private static final CppLanguage CPP = new CppLanguage();
    private static final List<JudgeStatus> ACTIVE = List.of(JudgeStatus.PENDING, JudgeStatus.JUDGING);
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    @Test
    void classifiesCppProcessFailures() {
        var sandbox = new ProcessSandbox();
        assertThat(sandbox.isMemoryLimitExceeded(CPP,
                new Sandbox.Result(false, false, 134, 10, "", "std::bad_alloc"))).isTrue();
        assertThat(sandbox.isMemoryLimitExceeded(CPP,
                new Sandbox.Result(false, false, 134, 10, "", "abort"))).isFalse();
        assertThat(sandbox.isMemoryLimitExceeded(CPP,
                new Sandbox.Result(false, false, 139, 10, "", "std::bad_alloc"))).isFalse();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("programs")
    void judgesCpp(String mode, JudgeStatus verdict, String code) throws InterruptedException {
        boolean docker = mode.equals("docker");
        assumeTrue(docker || verdict != JudgeStatus.MLE || !WINDOWS, "Windows Process 不限制 C++ 内存");
        assumeTrue(docker ? dockerAvailable() : supportsCpp17(),
                docker ? "Docker 或 gcc:13 镜像不可用" : "g++ 不可用或不支持 C++17");

        Sandbox sandbox = docker ? new DockerSandbox(1000) : new ProcessSandbox();
        var submissions = mock(SubmissionRepository.class);
        when(submissions.transition(1L, JudgeStatus.PENDING, JudgeStatus.JUDGING)).thenReturn(1);
        var judge = new JudgeEngine(submissions, sandbox, List.of(CPP), 1, 1, 10000);
        int limitMs = verdict == JudgeStatus.TLE ? 800 : 5000;
        try {
            judge.enqueue(new JudgeJob(1L, CPP.type(), code, limitMs, 32,
                    List.of(new TestCase(2L, "2 3\n", "5\n", true))));
            verify(submissions, timeout(25000)).finish(eq(1L), eq(ACTIVE), eq(verdict),
                    nullable(Long.class), nullable(String.class));
        } finally {
            judge.shutdown();
        }
    }

    private static Stream<Arguments> programs() {
        return Stream.of("process", "docker").flatMap(mode -> Stream.of(
                Arguments.of(mode, JudgeStatus.AC, program("int a, b; std::cin >> a >> b; std::cout << a + b;")),
                Arguments.of(mode, JudgeStatus.WA, program("std::cout << 0;")),
                Arguments.of(mode, JudgeStatus.TLE, program("volatile int running = 1; while (running) {}")),
                Arguments.of(mode, JudgeStatus.CE, "int main( {"),
                Arguments.of(mode, JudgeStatus.RE, program("""
                        volatile int numerator = 12345;
                        volatile int zero = 0;
                        std::cout << numerator / zero;
                        """)),
                Arguments.of(mode, JudgeStatus.MLE, program("""
                        constexpr int size = 192 * 1024 * 1024;
                        volatile char* data = new char[size];
                        for (int i = 0; i < size; i += 4096) data[i] = 1;
                        std::cout << data[0];
                        """))
        ));
    }

    private static String program(String body) {
        return "#include <iostream>\nint main() {\n" + body + "\n}\n";
    }

    private static boolean supportsCpp17() throws InterruptedException {
        return commandAvailable(List.of("g++", "-std=c++17", "-x", "c++", "-fsyntax-only", "-"), "int main() {}");
    }

    private static boolean dockerAvailable() throws InterruptedException {
        return commandAvailable(List.of("docker", "info"), "")
                && commandAvailable(List.of("docker", "image", "inspect", CPP.dockerImage()), "");
    }

    private static boolean commandAvailable(List<String> command, String input) throws InterruptedException {
        try {
            return new ProcessExecutor().execute(new ProcessBuilder(command), input, 6000).exitCode() == 0;
        } catch (IOException unavailable) {
            return false;
        }
    }
}
