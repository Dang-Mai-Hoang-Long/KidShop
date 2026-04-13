package com.example.demo.controller;

import com.example.demo.entity.ProductEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class ShopController {

    private final ProductService productService;

    public ShopController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/shop")
    public String shop(
            Model model,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "categoryName", required = false) String categoryName,
            @RequestParam(value = "flashSale", required = false) Boolean flashSale,
            @RequestParam(value = "page", defaultValue = "0") int page) {

        Page<ProductEntity> productPage;

        if (categoryId != null && (categoryName == null || categoryName.isBlank())) {
            String resolvedCategoryName = productService.getCategory(categoryId).getName();
            StringBuilder redirectUrl = new StringBuilder("redirect:/shop?categoryName=")
                    .append(URLEncoder.encode(resolvedCategoryName, StandardCharsets.UTF_8));
            if (page > 0) {
                redirectUrl.append("&page=").append(page);
            }
            if (flashSale != null && flashSale) {
                redirectUrl.append("&flashSale=true");
            }
            if (keyword != null && !keyword.isBlank()) {
                redirectUrl.append("&keyword=").append(URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8));
            }
            return redirectUrl.toString();
        }

        if (flashSale != null && flashSale) {
            productPage = productService.getFlashSaleProducts(PageRequest.of(Math.max(page, 0), 12));
            model.addAttribute("flashSale", true);
        } else if (categoryId != null) {
            productPage = productService.getProductsByCategory(categoryId, PageRequest.of(Math.max(page, 0), 12));
            model.addAttribute("selectedCategoryId", categoryId);
        } else if (categoryName != null && !categoryName.isBlank()) {
            productPage = productService.getProductsByCategoryName(categoryName.trim(), PageRequest.of(Math.max(page, 0), 12));
            model.addAttribute("selectedCategoryName", categoryName);
        } else if (keyword != null && !keyword.isBlank()) {
            productPage = productService.searchProducts(keyword.trim(), PageRequest.of(Math.max(page, 0), 12));
            model.addAttribute("keyword", keyword);
        } else {
            productPage = productService.searchProducts(null, PageRequest.of(Math.max(page, 0), 12));
        }

        model.addAttribute("productPage", productPage);
        model.addAttribute("categories", productService.getActiveCategories());
        return "shop";
    }

    @GetMapping("/product/{id}")
    public String productDetail(
            @PathVariable("id") Long id,
            Model model,
            @SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {

        ProductEntity product = productService.getProduct(id);
        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", productService.getBestSellers());
        return "product-detail";
    }
}
