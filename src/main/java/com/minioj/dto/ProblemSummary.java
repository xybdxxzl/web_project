package com.minioj.dto;

import com.minioj.entity.Difficulty;

public record ProblemSummary(Long id, String title, Difficulty difficulty, int timeLimitMs, int memoryLimitMb,
                             long submitCount, long acCount, UserStatus userStatus) {

    public enum UserStatus {
        AC, TRIED, NONE
    }
}
