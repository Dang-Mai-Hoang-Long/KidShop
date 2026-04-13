package com.example.demo.controller;

import com.example.demo.dto.LoginForm;
import com.example.demo.dto.SignupForm;
import com.example.demo.config.AuthCsrfTokenService;
import com.example.demo.model.CurrentUser;
import com.example.demo.service.AuthService;
import com.example.demo.validator.SignupFormValidator;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final AuthService authService;
    private final SignupFormValidator signupFormValidator;
    private final AuthCsrfTokenService authCsrfTokenService;

    public AuthController(AuthService authService, SignupFormValidator signupFormValidator, AuthCsrfTokenService authCsrfTokenService) {
        this.authService = authService;
        this.signupFormValidator = signupFormValidator;
        this.authCsrfTokenService = authCsrfTokenService;
    }

    @InitBinder("signupForm")
    public void initSignupBinder(WebDataBinder binder) {
        binder.addValidators(signupFormValidator);
    }

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request, HttpSession session, @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {
        if (currentUser != null && authService.isUserBanned(currentUser)) {
            session.invalidate();
            HttpSession newSession = request.getSession(true);
            newSession.setAttribute("loginErrorMessage", "Tài khoản của bạn đã bị khóa. Vui lòng đăng nhập lại sau khi được mở khóa.");
            currentUser = null;
        }

        if (currentUser != null) {
            return "redirect:/";
        }

        Object lockMessage = session.getAttribute("loginErrorMessage");
        if (lockMessage instanceof String message && !message.isBlank()) {
            model.addAttribute("loginError", message);
            session.removeAttribute("loginErrorMessage");
        }

        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm());
        }
        return "auth/login";
    }

    @PostMapping("/login")
    public String loginSubmit(
            @Valid @ModelAttribute("loginForm") LoginForm loginForm,
            BindingResult bindingResult,
            Model model,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "auth/login";
        }

        AuthService.LoginResult loginResult = authService.authenticate(loginForm);
        if (!loginResult.isSuccess()) {
            model.addAttribute("loginError", loginResult.getMessage());
            return "auth/login";
        }

        CurrentUser currentUser = loginResult.getCurrentUser();
        session.invalidate();
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("currentUser", currentUser);
        authCsrfTokenService.rotateToken(newSession);
        if ("ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            return "redirect:/admin";
        }

        return "redirect:/";
    }

    @GetMapping("/signup")
    public String signup(Model model, HttpServletRequest request, HttpSession session, @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {
        if (currentUser != null && authService.isUserBanned(currentUser)) {
            session.invalidate();
            HttpSession newSession = request.getSession(true);
            newSession.setAttribute("loginErrorMessage", "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
            currentUser = null;
        }

        if (currentUser != null) {
            return "redirect:/";
        }

        if (!model.containsAttribute("signupForm")) {
            model.addAttribute("signupForm", new SignupForm());
        }
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signupSubmit(
            @Valid @ModelAttribute("signupForm") SignupForm signupForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "auth/signup";
        }

        try {
            authService.register(signupForm);
        } catch (IllegalStateException exception) {
            model.addAttribute("signupError", exception.getMessage());
            return "auth/signup";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Đăng ký thành công. Vui lòng đăng nhập.");
        return "redirect:/login";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}