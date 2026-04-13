package com.example.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionAdvice {

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public String handleUploadError(HttpServletRequest request, Exception exception, RedirectAttributes redirectAttributes) {
        String referer = request.getHeader("Referer");
        String message = ExceptionMessageHelper.describe(exception);

        if (referer != null && referer.contains("/admin")) {
            redirectAttributes.addFlashAttribute("adminError", message);
            if (referer.contains("tab=products") || referer.contains("/admin/products")) {
                redirectAttributes.addFlashAttribute("showAddProductModal", true);
            }
            return "redirect:" + referer;
        }

        redirectAttributes.addFlashAttribute("profileError", message);

        if (referer != null && !referer.isBlank()) {
            return "redirect:" + referer;
        }

        return "redirect:/profile";
    }
}
