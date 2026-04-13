package com.example.demo.config;

import com.example.demo.model.CurrentUser;
import com.example.demo.service.CartService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttribute;

@ControllerAdvice
public class CartCountAdvice {

    private final CartService cartService;

    public CartCountAdvice(CartService cartService) {
        this.cartService = cartService;
    }

    @ModelAttribute("cartCount")
    public long cartCount(@SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {
        if (currentUser == null) {
            return 0;
        }
        return cartService.getCartCount(currentUser.getId());
    }
}
