package com.minioj.dto;

import java.util.List;

import com.minioj.entity.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProblemRequest(
        @NotBlank(message = "请输入题目标题")
        @Size(max = 200, message = "题目标题最多 200 字")
        String title,

        @NotBlank(message = "请输入题面")
        String description,

        @NotNull(message = "请输入时间限制")
        @Min(value = 1, message = "时间限制至少 1 毫秒")
        Integer timeLimitMs,

        @NotNull(message = "请输入内存限制")
        @Min(value = 1, message = "内存限制至少 1 MB")
        Integer memoryLimitMb,

        @NotNull(message = "请选择难度")
        Difficulty difficulty,

        @NotEmpty(message = "请至少添加一组测试用例")
        List<@NotNull(message = "测试用例不能为空") @Valid TestCaseRequest> testCases
) {
    public record TestCaseRequest(
            @NotNull(message = "请提供测试输入") String input,
            @NotNull(message = "请提供预期输出") String expectedOutput,
            @NotNull(message = "请设置是否为样例") Boolean isSample
    ) {
    }
}
