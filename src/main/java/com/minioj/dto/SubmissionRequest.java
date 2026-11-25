package com.minioj.dto;

import com.minioj.entity.LanguageType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmissionRequest(
        @NotNull @Min(1) Long problemId,
        @NotNull LanguageType language,
        @NotBlank @Size(max = 65536) String code
) {
}
