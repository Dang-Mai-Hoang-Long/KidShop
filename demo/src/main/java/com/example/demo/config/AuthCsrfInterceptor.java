package com.example.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthCsrfInterceptor implements HandlerInterceptor {

    private final AuthCsrfTokenService authCsrfTokenService;

    public AuthCsrfInterceptor(AuthCsrfTokenService authCsrfTokenService) {
        this.authCsrfTokenService = authCsrfTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isCsrfBypassPath(path)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null || !authCsrfTokenService.isValid(session, request.getParameter(AuthCsrfTokenService.REQUEST_PARAM))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token không hợp lệ");
            return false;
        }

        return true;
    }

    private boolean isCsrfBypassPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        return path.equals("/webhooks/sepay") || path.startsWith("/webhooks/sepay/");
    }
}