package com.minioj.service;

import java.util.List;
import java.util.Optional;

import com.minioj.entity.Difficulty;
import com.minioj.entity.Problem;
import com.minioj.entity.TestCase;
import com.minioj.repository.ProblemRepository;
import com.minioj.repository.SubmissionRepository;
import com.minioj.repository.TestCaseRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ProblemServiceTest {

    @Test
    void publicDetailQueriesSamplesOnly() {
        ProblemRepository problems = mock(ProblemRepository.class);
        TestCaseRepository cases = mock(TestCaseRepository.class);
        ProblemService service = new ProblemService(problems, cases, mock(SubmissionRepository.class));
        when(problems.findById(1L)).thenReturn(Optional.of(
                new Problem("A+B", "题面", 1000, 128, Difficulty.EASY)));
        when(cases.findByProblemIdAndSampleTrueOrderByIdAsc(1L)).thenReturn(List.of(
                new TestCase(1L, "1 2", "3", true)));

        var detail = service.detail(1L, false);

        assertThat(detail.testCases()).singleElement().satisfies(sample -> {
            assertThat(sample.expectedOutput()).isEqualTo("3");
            assertThat(sample.isSample()).isTrue();
        });
        verify(cases).findByProblemIdAndSampleTrueOrderByIdAsc(1L);
        verifyNoMoreInteractions(cases);
    }
}
