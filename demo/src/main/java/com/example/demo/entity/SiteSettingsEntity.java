package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "site_settings")
public class SiteSettingsEntity implements Serializable {

    @Id
    private Long id = 1L;

    @Column(length = 255)
    private String shopName = "MiniWear Kids";

    @Column(length = 500)
    private String logoPath = "/assets/logo.png";

    @Column(columnDefinition = "TEXT")
    private String footerText = "Bản quyền thuộc về MiniWear Kids.";

    @Column(length = 120)
    private String footerPhone = "0901 234 567";

    @Column(length = 255)
    private String footerAddress = "Ho Chi Minh City";

    @Column(length = 160)
    private String footerShippingText = "Giao nhanh toàn quốc";

    @Column(length = 120)
    private String heroBadgeText = "BỘ SƯU TẬP THÁNG 3";

    @Column(length = 255)
    private String heroTitle = "Quần áo trẻ em dễ thương, dễ mặc, dễ mua";

    @Column(columnDefinition = "TEXT")
    private String heroDescription = "MiniWear Kids tập trung vào chất liệu mềm, thiết kế gọn gàng và giá hợp lý để bé mặc thoải mái mỗi ngày.";

    @Column(length = 80)
    private String heroPrimaryButtonText = "Xem cửa hàng";

    @Column(length = 255)
    private String heroPrimaryButtonUrl = "/shop";

    @Column(length = 80)
    private String heroSecondaryButtonText = "Sản phẩm bán chạy";

    @Column(length = 255)
    private String heroSecondaryButtonUrl = "#best-seller";

    @Column(length = 40)
    private String heroStat1Value = "2.000+";

    @Column(length = 120)
    private String heroStat1Label = "Đơn đã giao";

    @Column(length = 40)
    private String heroStat2Value = "4.9/5";

    @Column(length = 120)
    private String heroStat2Label = "Điểm đánh giá";

    @Column(length = 40)
    private String heroStat3Value = "48h";

    @Column(length = 120)
    private String heroStat3Label = "Đổi trả linh hoạt";

    @Column
    private boolean flashSaleEnabled = true;

    @Column
    private LocalDateTime flashSaleEndTime;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public String getFooterText() { return footerText; }
    public void setFooterText(String footerText) { this.footerText = footerText; }

    public String getFooterPhone() { return footerPhone; }
    public void setFooterPhone(String footerPhone) { this.footerPhone = footerPhone; }

    public String getFooterAddress() { return footerAddress; }
    public void setFooterAddress(String footerAddress) { this.footerAddress = footerAddress; }

    public String getFooterShippingText() { return footerShippingText; }
    public void setFooterShippingText(String footerShippingText) { this.footerShippingText = footerShippingText; }

    public String getHeroBadgeText() { return heroBadgeText; }
    public void setHeroBadgeText(String heroBadgeText) { this.heroBadgeText = heroBadgeText; }

    public String getHeroTitle() { return heroTitle; }
    public void setHeroTitle(String heroTitle) { this.heroTitle = heroTitle; }

    public String getHeroDescription() { return heroDescription; }
    public void setHeroDescription(String heroDescription) { this.heroDescription = heroDescription; }

    public String getHeroPrimaryButtonText() { return heroPrimaryButtonText; }
    public void setHeroPrimaryButtonText(String heroPrimaryButtonText) { this.heroPrimaryButtonText = heroPrimaryButtonText; }

    public String getHeroPrimaryButtonUrl() { return heroPrimaryButtonUrl; }
    public void setHeroPrimaryButtonUrl(String heroPrimaryButtonUrl) { this.heroPrimaryButtonUrl = heroPrimaryButtonUrl; }

    public String getHeroSecondaryButtonText() { return heroSecondaryButtonText; }
    public void setHeroSecondaryButtonText(String heroSecondaryButtonText) { this.heroSecondaryButtonText = heroSecondaryButtonText; }

    public String getHeroSecondaryButtonUrl() { return heroSecondaryButtonUrl; }
    public void setHeroSecondaryButtonUrl(String heroSecondaryButtonUrl) { this.heroSecondaryButtonUrl = heroSecondaryButtonUrl; }

    public String getHeroStat1Value() { return heroStat1Value; }
    public void setHeroStat1Value(String heroStat1Value) { this.heroStat1Value = heroStat1Value; }

    public String getHeroStat1Label() { return heroStat1Label; }
    public void setHeroStat1Label(String heroStat1Label) { this.heroStat1Label = heroStat1Label; }

    public String getHeroStat2Value() { return heroStat2Value; }
    public void setHeroStat2Value(String heroStat2Value) { this.heroStat2Value = heroStat2Value; }

    public String getHeroStat2Label() { return heroStat2Label; }
    public void setHeroStat2Label(String heroStat2Label) { this.heroStat2Label = heroStat2Label; }

    public String getHeroStat3Value() { return heroStat3Value; }
    public void setHeroStat3Value(String heroStat3Value) { this.heroStat3Value = heroStat3Value; }

    public String getHeroStat3Label() { return heroStat3Label; }
    public void setHeroStat3Label(String heroStat3Label) { this.heroStat3Label = heroStat3Label; }

    public boolean isFlashSaleEnabled() { return flashSaleEnabled; }
    public void setFlashSaleEnabled(boolean flashSaleEnabled) { this.flashSaleEnabled = flashSaleEnabled; }

    public LocalDateTime getFlashSaleEndTime() { return flashSaleEndTime; }
    public void setFlashSaleEndTime(LocalDateTime flashSaleEndTime) { this.flashSaleEndTime = flashSaleEndTime; }
}
