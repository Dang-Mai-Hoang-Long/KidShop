package com.example.demo.config;

import com.example.demo.entity.NotificationEntity;
import com.example.demo.entity.SiteSettingsEntity;
import com.example.demo.model.CurrentUser;
import com.example.demo.repository.SiteSettingsRepository;
import com.example.demo.service.NotificationService;
import com.example.demo.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    private static final String SITE_SETTINGS_CACHE_KEY = GlobalControllerAdvice.class.getName() + ".siteSettings";

    @Autowired
    private SiteSettingsRepository siteSettingsRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ProductService productService;

    @ModelAttribute("siteSettings")
    public SiteSettingsEntity getSettings(HttpServletRequest request) {
        return resolveSiteSettings(request);
    }

    @ModelAttribute("siteDisplayName")
    public String getSiteDisplayName(HttpServletRequest request) {
        SiteSettingsEntity settings = resolveSiteSettings(request);
        String shopName = settings.getShopName();

        return shopName != null ? shopName.trim() : "";
    }

    @ModelAttribute("siteDisplayLogoPath")
    public String getSiteDisplayLogoPath(HttpServletRequest request) {
        SiteSettingsEntity settings = resolveSiteSettings(request);
        String logoPath = settings.getLogoPath();

        return logoPath != null ? logoPath.trim() : "";
    }

    private SiteSettingsEntity resolveSiteSettings(HttpServletRequest request) {
        Object cached = request.getAttribute(SITE_SETTINGS_CACHE_KEY);
        if (cached instanceof SiteSettingsEntity settings) {
            return settings;
        }

        SiteSettingsEntity settings = siteSettingsRepository.findById(1L)
                .orElseGet(() -> siteSettingsRepository.save(new SiteSettingsEntity()));

        settings = expireFlashSaleOnDemand(settings);

        request.setAttribute(SITE_SETTINGS_CACHE_KEY, settings);
        return settings;
    }

    private SiteSettingsEntity expireFlashSaleOnDemand(SiteSettingsEntity settings) {
        if (settings == null) {
            return settings;
        }

        // Always clean up truly expired/sold-out items, regardless of global enable flag.
        productService.resetExpiredFlashSaleProducts();

        if (!settings.isFlashSaleEnabled()) {
            return settings;
        }

        LocalDateTime endTime = settings.getFlashSaleEndTime();
        if (endTime == null || LocalDateTime.now().isBefore(endTime)) {
            return settings;
        }

        settings.setFlashSaleEnabled(false);
        SiteSettingsEntity savedSettings = siteSettingsRepository.save(settings);
        productService.resetAllFlashSaleProducts();
        return savedSettings;
    }

    @ModelAttribute("recentNotifications")
    public List<NotificationEntity> getRecentNotifications(@SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {
        if (currentUser == null) {
            return Collections.emptyList();
        }

        return notificationService.getRecentNotifications(currentUser.getId(), 10);
    }

    @ModelAttribute("notificationUnreadCount")
    public long getUnreadNotificationCount(@SessionAttribute(value = "currentUser", required = false) CurrentUser currentUser) {
        if (currentUser == null) {
            return 0L;
        }

        return notificationService.countUnread(currentUser.getId());
    }
}
