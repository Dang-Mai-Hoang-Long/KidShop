package com.example.demo.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AuthCsrfTokenService {

    public static final String SESSION_KEY = "AUTH_CSRF_TOKEN";
    public static final String REQUEST_PARAM = "authCsrfToken";

    private final SecureRandom secureRandom = new SecureRandom();

    public String getOrCreateToken(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof String token && !token.isBlank()) {
            return token;
        }

        String generated = generateToken();
        session.setAttribute(SESSION_KEY, generated);
        return generated;
    }

    public void rotateToken(HttpSession session) {
        session.setAttribute(SESSION_KEY, generateToken());
    }

    public boolean isValid(HttpSession session, String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return false;
        }

        Object stored = session.getAttribute(SESSION_KEY);
        return stored instanceof String token && token.equals(presentedToken);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}