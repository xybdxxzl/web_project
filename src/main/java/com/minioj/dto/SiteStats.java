package com.minioj.dto;

import java.time.Instant;
import java.util.List;

import com.minioj.entity.JudgeStatus;
import com.minioj.entity.LanguageType;

public record SiteStats(long totalProblems, long totalUsers, long totalSubmissions, double acRate,
                        List<RecentSubmission> recentSubmissions) {

    public record RecentSubmission(Long id, String username, Long problemId, String problemTitle,
                                   LanguageType language, JudgeStatus status, Instant createdAt) {
    }
}
