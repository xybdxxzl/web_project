package com.minioj.dto;

import java.util.List;

public record SubmissionPage(List<SubmissionSummary> items, int page, int size, long total, int totalPages) {
}
