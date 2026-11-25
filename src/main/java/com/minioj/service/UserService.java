package com.minioj.service;

import com.minioj.dto.CurrentUser;
import com.minioj.entity.Role;
import com.minioj.entity.User;
import com.minioj.exception.BizException;
import com.minioj.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final String ADMIN_USERNAME = "admin";

    private final UserRepository users;
    private final PasswordHasher passwords;

    UserService(UserRepository users, PasswordHasher passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public CurrentUser register(String username, String password) {
        if (ADMIN_USERNAME.equals(username) || users.existsByUsername(username)) {
            throw new BizException(HttpStatus.CONFLICT, "用户名已存在");
        }

        User user = users.saveAndFlush(new User(username, passwords.encode(password), Role.USER));
        return new CurrentUser(user.getId(), user.getUsername(), user.getRole());
    }

    @Transactional(readOnly = true)
    public CurrentUser authenticate(String username, String password) {
        User user = users.findByUsername(username)
                .filter(candidate -> passwords.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new BizException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        return new CurrentUser(user.getId(), user.getUsername(), user.getRole());
    }

    @Transactional
    public void initializeAdmin(String password) {
        var existing = users.findByUsername(ADMIN_USERNAME);
        if (existing.isEmpty()) {
            users.save(new User(ADMIN_USERNAME, passwords.encode(password), Role.ADMIN));
            return;
        }
        if (existing.get().getRole() != Role.ADMIN) {
            throw new IllegalStateException("用户名 admin 已被普通账号使用");
        }
    }
}
