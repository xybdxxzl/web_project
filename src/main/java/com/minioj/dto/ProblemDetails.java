package com.minioj.dto;

import java.time.Instant;
import java.util.List;

import com.minioj.entity.Difficulty;

public record ProblemDetails(
        Long id, String title, String description, Difficulty difficulty,
        int timeLimitMs, int memoryLimitMb, Instant createdAt, List<TestCaseView> testCases
) {
    public record TestCaseView(String input, String expectedOutput, boolean isSample) {
    }
}
