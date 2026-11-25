package com.minioj.judge;

import java.io.IOException;
import java.util.List;

import com.minioj.entity.JudgeStatus;
import com.minioj.entity.LanguageType;
import com.minioj.entity.TestCase;
import com.minioj.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JudgeEngineTest {

    private static final List<JudgeStatus> ACTIVE = List.of(JudgeStatus.PENDING, JudgeStatus.JUDGING);

    @Test
    void recordsUnexpectedFailureAndRemovesWorkingDirectory() throws Exception {
        var submissions = mock(SubmissionRepository.class);
        var sandbox = mock(Sandbox.class);
        var language = new JavaLanguage();
        when(submissions.transition(1L, JudgeStatus.PENDING, JudgeStatus.JUDGING)).thenReturn(1);
        when(sandbox.compile(eq(language), any(), eq(1000), eq(32))).thenThrow(new IOException("执行环境错误"));
        var judge = new JudgeEngine(submissions, sandbox, List.of(language), 1, 1, 1000);

        try {
            judge.enqueue(new JudgeJob(1L, LanguageType.JAVA, "code", 100, 32, List.of()));
            verify(submissions, timeout(5000)).finish(eq(1L), eq(ACTIVE), eq(JudgeStatus.SE),
                    isNull(), contains("IOException"));
            verify(submissions, never()).setTotalCases(eq(1L), anyInt());
            verify(submissions, never()).incrementPassedCases(1L);
            var workspace = ArgumentCaptor.forClass(Sandbox.Workspace.class);
            verify(sandbox).compile(eq(language), workspace.capture(), eq(1000), eq(32));
            var work = workspace.getValue().directory();
            assertThat(work.getFileName().toString()).isEqualTo("work");
            assertThat(work.getParent()).doesNotExist();
        } finally {
            judge.shutdown();
        }
    }

    @Test
    void keepsMaximumTimeAndSkipsCasesAfterFirstFailure() throws Exception {
        var submissions = mock(SubmissionRepository.class);
        var sandbox = mock(Sandbox.class);
        var language = new JavaLanguage();
        when(submissions.transition(1L, JudgeStatus.PENDING, JudgeStatus.JUDGING)).thenReturn(1);
        when(sandbox.compile(eq(language), any(), eq(1000), eq(32)))
                .thenReturn(new Sandbox.Result(false, false, 0, 100, "", ""));
        when(sandbox.run(eq(language), any(), eq(1), eq("first"), eq(100), eq(32)))
                .thenReturn(new Sandbox.Result(false, false, 0, 42, "  exact  \r\n\n", ""));
        when(sandbox.run(eq(language), any(), eq(2), eq("second"), eq(100), eq(32)))
                .thenReturn(new Sandbox.Result(false, false, 0, 6, "wrong", ""));
        var judge = new JudgeEngine(submissions, sandbox, List.of(language), 1, 1, 1000);
        var cases = List.of(new TestCase(2L, "first", "  exact\n", true),
                new TestCase(2L, "second", "answer", false),
                new TestCase(2L, "skip", "answer", false));

        try {
            judge.enqueue(new JudgeJob(1L, LanguageType.JAVA, "code", 100, 32, cases));
            verify(submissions, timeout(5000)).finish(eq(1L), eq(ACTIVE), eq(JudgeStatus.WA),
                    eq(42L), contains("输出"));
            var order = inOrder(submissions, sandbox);
            order.verify(submissions).setTotalCases(1L, 3);
            order.verify(sandbox).run(eq(language), any(), eq(1), eq("first"), eq(100), eq(32));
            order.verify(submissions).incrementPassedCases(1L);
            order.verify(sandbox).run(eq(language), any(), eq(2), eq("second"), eq(100), eq(32));
            verify(submissions).incrementPassedCases(1L);
            verify(sandbox, never()).run(eq(language), any(), eq(3), eq("skip"), eq(100), eq(32));
        } finally {
            judge.shutdown();
        }
    }
}
