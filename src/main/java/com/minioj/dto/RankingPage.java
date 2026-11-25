package com.minioj.dto;

import java.util.List;

public record RankingPage(List<Entry> items, int page, int size, long total, int totalPages) {

    public record Entry(long rank, Long userId, String username, long acProblemCount, long totalSubmissions,
                        double acRate) {
    }
}
