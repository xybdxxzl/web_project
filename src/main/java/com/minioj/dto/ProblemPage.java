package com.minioj.dto;

import java.util.List;

public record ProblemPage(List<ProblemSummary> items, int page, int size, long total, int totalPages) {
}
