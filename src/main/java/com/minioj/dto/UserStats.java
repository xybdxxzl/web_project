package com.minioj.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.minioj.entity.Difficulty;

public record UserStats(String username, long totalSubmissions, long acProblemCount,
                        Map<Difficulty, DifficultyStats> difficultyStats,
                        List<DailyActivity> dailyTrend, List<CalendarDay> yearlyCalendar) {

    public record DifficultyStats(long solved, long total) {
    }

    public record DailyActivity(LocalDate date, long total, long ac) {
    }

    public record CalendarDay(LocalDate date, long count) {
    }
}
