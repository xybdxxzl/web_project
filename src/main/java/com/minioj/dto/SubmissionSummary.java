package com.minioj.dto;

import java.time.Instant;

import com.minioj.entity.JudgeStatus;
import com.minioj.entity.LanguageType;

public record SubmissionSummary(Long submissionId, Long problemId, String problemTitle, LanguageType language,
                                JudgeStatus status, Long timeUsedMs, Instant createdAt) {
}
