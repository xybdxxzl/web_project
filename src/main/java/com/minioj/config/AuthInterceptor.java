package com.minioj.config;

import com.minioj.dto.CurrentUser;
import com.minioj.entity.Role;
import com.minioj.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER = "currentUser";

    private final boolean adminOnly;

    public AuthInterceptor(boolean adminOnly) {
        this.adminOnly = adminOnly;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SESSION_USER) instanceof CurrentUser user)) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (adminOnly && user.role() != Role.ADMIN) {
            throw new BizException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
        return true;
    }
}
