package com.minioj.controller;

import com.minioj.dto.ApiResponse;
import com.minioj.dto.CredentialsRequest;
import com.minioj.dto.CurrentUser;
import com.minioj.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import static com.minioj.config.AuthInterceptor.SESSION_USER;

@RestController
public class AuthController {

    private final UserService users;

    public AuthController(UserService users) {
        this.users = users;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CurrentUser> register(@Valid @RequestBody CredentialsRequest credentials) {
        return ApiResponse.success(users.register(credentials.username(), credentials.password()));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<CurrentUser>> login(
            @Valid @RequestBody CredentialsRequest credentials, HttpServletRequest request) {
        CurrentUser user = users.authenticate(credentials.username(), credentials.password());
        HttpSession session = request.getSession();
        request.changeSessionId();
        session.setAttribute(SESSION_USER, user);
        return sessionResponse(user);
    }

    @PostMapping(value = "/api/auth/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.success(null);
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<ApiResponse<CurrentUser>> currentUser(@SessionAttribute(SESSION_USER) CurrentUser user) {
        return sessionResponse(user);
    }

    private ResponseEntity<ApiResponse<CurrentUser>> sessionResponse(CurrentUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(user));
    }
}
