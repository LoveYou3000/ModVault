package com.modvault.web;

import com.modvault.common.ApiException;
import com.modvault.service.AdminTokenValidator;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Admin-plane authentication via the static {@code MODVAULT_ADMIN_TOKEN} bearer token.
 * Data-plane keys and the admin token are not interchangeable (FR-24).
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminTokenValidator validator;

    public AdminAuthInterceptor(AdminTokenValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw ApiException.unauthorized("missing admin token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!validator.matches(token)) {
            throw ApiException.unauthorized("invalid admin token");
        }
        return true;
    }
}
