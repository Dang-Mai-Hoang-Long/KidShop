package com.example.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AuthCsrfAdvice {

    private final AuthCsrfTokenService authCsrfTokenService;

    public AuthCsrfAdvice(AuthCsrfTokenService authCsrfTokenService) {
        this.authCsrfTokenService = authCsrfTokenService;
    }

    @ModelAttribute("authCsrfToken")
    public String authCsrfToken(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        return authCsrfTokenService.getOrCreateToken(session);
    }
}