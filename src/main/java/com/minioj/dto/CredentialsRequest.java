package com.minioj.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CredentialsRequest(
        @NotNull(message = "请输入用户名")
        @Pattern(regexp = "[a-zA-Z0-9_]{3,20}", message = "用户名须为 3–20 位字母、数字或下划线")
        String username,

        @NotNull(message = "请输入密码")
        @Size(min = CredentialsRequest.MIN_PASSWORD_LENGTH, max = CredentialsRequest.MAX_PASSWORD_LENGTH,
                message = "密码须为 6–32 位")
        String password
) {
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 32;
}
