package com.example.demo.config;

import com.example.demo.entity.UserAccountEntity;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

@Component
public class LockedAccountInterceptor implements HandlerInterceptor {

    private final UserAccountRepository userAccountRepository;
    private final AuthService authService;

    public LockedAccountInterceptor(UserAccountRepository userAccountRepository, AuthService authService) {
        this.userAccountRepository = userAccountRepository;
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!isProtectedPath(request.getMethod(), path)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return true;
        }

        Object currentUserAttribute = session.getAttribute("currentUser");
        if (!(currentUserAttribute instanceof com.example.demo.model.CurrentUser currentUser)) {
            return true;
        }

        UserAccountEntity userAccount = userAccountRepository.findById(currentUser.getId()).orElse(null);
        if (userAccount == null) {
            session.invalidate();
            return true;
        }

        if (!authService.isAccountBlocked(userAccount, LocalDateTime.now())) {
            return true;
        }

        String lockMessage = authService.describeLockMessage(userAccount, LocalDateTime.now());
        session.invalidate();
        HttpSession freshSession = request.getSession(true);
        freshSession.setAttribute("loginErrorMessage", lockMessage);
        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }

    private boolean isProtectedPath(String method, String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        if (path.startsWith("/assets/") || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")) {
            return false;
        }

        if ("/login".equals(path) || "/signup".equals(path) || "/logout".equals(path) || "/".equals(path) || "/landing".equals(path) || path.startsWith("/shop") || path.startsWith("/product/")) {
            return false;
        }

        return path.startsWith("/profile") || path.startsWith("/account") || path.startsWith("/cart") || path.startsWith("/checkout") || path.startsWith("/orders") || path.startsWith("/admin");
    }
}