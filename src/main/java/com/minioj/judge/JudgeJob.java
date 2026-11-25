package com.minioj.judge;

import java.util.List;

import com.minioj.entity.LanguageType;
import com.minioj.entity.TestCase;

public record JudgeJob(Long submissionId, LanguageType language, String code,
                       int timeLimitMs, int memoryLimitMb, List<TestCase> testCases) {
}
