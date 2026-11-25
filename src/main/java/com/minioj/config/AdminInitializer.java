package com.minioj.config;

import com.minioj.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import static com.minioj.dto.CredentialsRequest.MAX_PASSWORD_LENGTH;
import static com.minioj.dto.CredentialsRequest.MIN_PASSWORD_LENGTH;

@Component
class AdminInitializer implements ApplicationRunner {

    private final UserService users;
    private final String password;

    AdminInitializer(UserService users, @Value("${MINIOJ_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isEmpty()) {
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("MINIOJ_ADMIN_PASSWORD 的长度须为 6–32 位");
        }
        users.initializeAdmin(password);
    }
}
