package com.example.demo.config;

import com.example.demo.model.CurrentUser;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	private final AuthService authService;
	private final AuthCsrfTokenService authCsrfTokenService;

	public SecurityConfig(AuthService authService, AuthCsrfTokenService authCsrfTokenService) {
		this.authService = authService;
		this.authCsrfTokenService = authCsrfTokenService;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
				.oauth2Login(oauth -> oauth
						.loginPage("/login")
						.successHandler((request, response, authentication) -> {
							OAuth2AuthenticationToken authenticationToken = (OAuth2AuthenticationToken) authentication;
							OAuth2User oauth2User = authenticationToken.getPrincipal();

							String email = oauth2User.getAttribute("email");
							String sub = oauth2User.getAttribute("sub");
							String givenName = oauth2User.getAttribute("given_name");
							String familyName = oauth2User.getAttribute("family_name");
							String fullName = oauth2User.getAttribute("name");
							String pictureUrl = oauth2User.getAttribute("picture");
							boolean emailVerified = extractBooleanAttribute(oauth2User.getAttribute("email_verified"));

							HttpSession session = request.getSession(true);
							Long pendingLinkUserId = extractLong(session.getAttribute("pendingGoogleLinkUserId"));

							AuthService.GoogleAuthResult googleAuthResult = authService.authenticateWithGoogle(
									email,
									sub,
									emailVerified,
									givenName,
									familyName,
									fullName,
									pictureUrl,
									pendingLinkUserId);

							session.removeAttribute("pendingGoogleLinkUserId");

							if (!googleAuthResult.isSuccess()) {
								if (googleAuthResult.isProfileFlow()) {
									session.setAttribute("profileOAuthError", googleAuthResult.getMessage());
								} else {
									session.setAttribute("loginErrorMessage", googleAuthResult.getMessage());
								}
								response.sendRedirect(resolveRedirectPath(request, googleAuthResult.getRedirectPath()));
								return;
							}

							CurrentUser currentUser = googleAuthResult.getCurrentUser();
							if (currentUser == null) {
								session.setAttribute("loginErrorMessage", "Đăng nhập Google thất bại. Vui lòng thử lại.");
								response.sendRedirect(resolveRedirectPath(request, "/login"));
								return;
							}

							session.setAttribute("currentUser", currentUser);
							authCsrfTokenService.rotateToken(session);

							if (googleAuthResult.getMessage() != null && !googleAuthResult.getMessage().isBlank()) {
								if (googleAuthResult.isProfileFlow()) {
									session.setAttribute("profileOAuthSuccess", googleAuthResult.getMessage());
								} else {
									session.setAttribute("oauthSuccessMessage", googleAuthResult.getMessage());
								}
							}

							response.sendRedirect(resolveRedirectPath(request, googleAuthResult.getRedirectPath()));
						})
						.failureHandler((request, response, exception) -> {
							HttpSession session = request.getSession(true);
							session.setAttribute("loginErrorMessage", "Không thể đăng nhập bằng Google. Vui lòng thử lại.");
							response.sendRedirect(resolveRedirectPath(request, "/login"));
						})
				)
				.oauth2Client(Customizer.withDefaults());

		return http.build();
	}

	private String resolveRedirectPath(HttpServletRequest request, String path) {
		String contextPath = request.getContextPath();
		if (path == null || path.isBlank()) {
			path = "/";
		}
		return contextPath + path;
	}

	private boolean extractBooleanAttribute(Object value) {
		if (value instanceof Boolean boolValue) {
			return boolValue;
		}
		if (value instanceof String stringValue) {
			return Boolean.parseBoolean(stringValue);
		}
		return false;
	}

	private Long extractLong(Object value) {
		if (value instanceof Long longValue) {
			return longValue;
		}
		if (value instanceof Number numberValue) {
			return numberValue.longValue();
		}
		if (value instanceof String stringValue && !stringValue.isBlank()) {
			try {
				return Long.parseLong(stringValue.trim());
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}
}
