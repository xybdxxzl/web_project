package com.minioj.dto;

import com.minioj.entity.Role;

public record CurrentUser(Long id, String username, Role role) {
}
