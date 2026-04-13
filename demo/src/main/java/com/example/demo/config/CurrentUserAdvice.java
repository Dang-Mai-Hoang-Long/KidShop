package com.example.demo.config;

import com.example.demo.model.CurrentUser;
import com.example.demo.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttribute;

@ControllerAdvice
public class CurrentUserAdvice {

    private final OrderService orderService;

    public CurrentUserAdvice(OrderService orderService) {
        this.orderService = orderService;
    }

    @ModelAttribute("currentUser")
    public CurrentUser currentUser(
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            HttpServletRequest request) {
        if (currentUser == null || "ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            return currentUser;
        }

        CurrentUser refreshedCurrentUser = orderService.refreshUserRankByDeliveredOrders(currentUser.getId());
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute("currentUser", refreshedCurrentUser);
        }
        return refreshedCurrentUser;
    }
}