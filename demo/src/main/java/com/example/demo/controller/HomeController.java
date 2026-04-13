package com.example.demo.controller;

import com.example.demo.entity.BannerEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;

@Controller
public class HomeController {

    private final ProductService productService;

    public HomeController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping({"/", "/landing"})
    public String landing(Model model,
                          @RequestParam(value = "keyword", required = false) String keyword,
                          @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {

        model.addAttribute("bestSellers", productService.getBestSellers());
        model.addAttribute("featuredProducts", productService.getFeaturedProducts());
        model.addAttribute("flashSaleProducts", productService.getFlashSaleProducts());
        model.addAttribute("categories", productService.getActiveCategories());
        model.addAttribute("newestProducts", productService.getNewestProducts());

        // Dynamic banners
        List<BannerEntity> banners = productService.getActiveBanners();
        model.addAttribute("banners", banners);

        // Admin edit mode flag
        boolean isAdmin = currentUser != null && "ADMIN".equalsIgnoreCase(currentUser.getRole());
        model.addAttribute("isAdmin", isAdmin);

        // All banners for admin edit mode
        if (isAdmin) {
            model.addAttribute("allBanners", productService.getAllBanners());
        }

        if (keyword != null && !keyword.isBlank()) {
            return "redirect:/shop?keyword=" + keyword.trim();
        }

        return "index";
    }
}