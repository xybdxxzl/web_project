package com.minioj.dto;

import java.time.Instant;

import com.minioj.entity.JudgeStatus;
import com.minioj.entity.LanguageType;

public record SubmissionResult(Long submissionId, Long problemId, LanguageType language, JudgeStatus status,
                               Long timeUsedMs, Long memoryUsedKb, String errorMessage, String code, Instant createdAt,
                               int passedCases, int totalCases) {
}
