package com.example.demo.scheduler;

import com.example.demo.entity.SiteSettingsEntity;
import com.example.demo.repository.SiteSettingsRepository;
import com.example.demo.service.ProductService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduler that automatically disables Flash Sale when the end time has passed,
 * and resets all flash-sale products back to regular pricing.
 */
@Component
public class FlashSaleScheduler {

    private final SiteSettingsRepository siteSettingsRepository;
    private final ProductService productService;

    public FlashSaleScheduler(SiteSettingsRepository siteSettingsRepository,
                              ProductService productService) {
        this.siteSettingsRepository = siteSettingsRepository;
        this.productService = productService;
    }

    /** Check every 60 seconds. */
    @Scheduled(fixedDelay = 60_000)
    public void expireFlashSaleIfNeeded() {
        SiteSettingsEntity settings = siteSettingsRepository.findById(1L).orElse(null);
        if (settings == null) {
            return;
        }

        int expiredProductCount = productService.resetExpiredFlashSaleProducts();

        if (!settings.isFlashSaleEnabled()) {
            return;
        }

        LocalDateTime endTime = settings.getFlashSaleEndTime();
        if (endTime != null && !LocalDateTime.now().isBefore(endTime)) {
            // 1. Disable flash sale flag
            settings.setFlashSaleEnabled(false);
            siteSettingsRepository.save(settings);

            // 2. Reset all products that were marked as flash sale
            int resetCount = productService.resetAllFlashSaleProducts();

            System.out.println("[FlashSaleScheduler] Flash Sale expired at " + LocalDateTime.now()
                    + ", cleaned " + (resetCount + expiredProductCount) + " products.");
        }
    }
}
