package com.example.demo.controller;

import com.example.demo.entity.CartItemEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.service.AuthService;
import com.example.demo.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CartController {

    private final CartService cartService;
    private final AuthService authService;

    public CartController(CartService cartService, AuthService authService) {
        this.cartService = cartService;
        this.authService = authService;
    }

    @GetMapping("/cart")
    public String viewCart(Model model, @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser, RedirectAttributes redirectAttributes) {
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (authService.isUserBanned(currentUser)) {
            redirectAttributes.addFlashAttribute("error", "Tài khoản của bạn đã bị khóa. Không thể thực hiện thao tác này.");
            return "redirect:/";
        }

        List<CartItemEntity> items = cartService.getCartItems(currentUser.getId());
        BigDecimal total = cartService.getCartTotal(currentUser.getId());

        model.addAttribute("cartItems", items);
        model.addAttribute("cartTotal", total);
        model.addAttribute("formattedTotal", String.format("%,.0f", total) + "đ");
        return "cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "quantity", defaultValue = "1") int quantity,
            @RequestParam(value = "size", required = false) String size,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        if (authService.isUserBanned(currentUser)) {
            redirectAttributes.addFlashAttribute("error", "Tài khoản của bạn đã bị khóa. Không thể thực hiện thao tác này.");
            return "redirect:/";
        }

        try {
            cartService.addToCart(currentUser.getId(), productId, quantity, size);
            redirectAttributes.addFlashAttribute("cartSuccess", "Đã thêm sản phẩm vào giỏ hàng!");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("cartError", ex.getMessage());
        }

        return "redirect:/cart";
    }

    @PostMapping("/cart/add-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addToCartAjax(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "quantity", defaultValue = "1") int quantity,
            @RequestParam(value = "size", required = false) String size,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {

        Map<String, Object> payload = new HashMap<>();

        if (currentUser == null) {
            payload.put("success", false);
            payload.put("requiresLogin", true);
            payload.put("redirectUrl", "/login");
            payload.put("message", "Vui lòng đăng nhập để thêm vào giỏ hàng.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(payload);
        }

        if (authService.isUserBanned(currentUser)) {
            payload.put("success", false);
            payload.put("message", "Tài khoản của bạn đã bị khóa. Không thể thực hiện thao tác này.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(payload);
        }

        try {
            cartService.addToCart(currentUser.getId(), productId, quantity, size);
            payload.put("success", true);
            payload.put("message", "Đã thêm sản phẩm vào giỏ hàng!");
            payload.put("cartCount", cartService.getCartCount(currentUser.getId()));
            return ResponseEntity.ok(payload);
        } catch (IllegalStateException ex) {
            payload.put("success", false);
            payload.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(payload);
        }
    }

    @PostMapping("/cart/update")
    public String updateCartItem(
            @RequestParam("cartItemId") Long cartItemId,
            @RequestParam("quantity") int quantity,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            cartService.updateQuantity(currentUser.getId(), cartItemId, quantity);
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("cartError", ex.getMessage());
        }

        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(
            @RequestParam("cartItemId") Long cartItemId,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser,
            RedirectAttributes redirectAttributes) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            cartService.removeFromCart(currentUser.getId(), cartItemId);
            redirectAttributes.addFlashAttribute("cartSuccess", "Đã xóa sản phẩm khỏi giỏ hàng.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("cartError", ex.getMessage());
        }

        return "redirect:/cart";
    }
}
