package com.example.demo.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class ProductEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    @Column(precision = 12, scale = 0)
    private BigDecimal originalPrice;

    @Column(nullable = false)
    private int stock = 0;

    @Column(length = 500)
    private String imagePath;

    @Column(length = 500)
    private String imagePath2;

    @Column(length = 500)
    private String imagePath3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity category;

    @Column(length = 50)
    private String ageRange;

    @Column(length = 100)
    private String sizes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, unique = true, length = 30)
    private String productCode;

    @Column(nullable = false)
    private boolean featured = false;

    @Column(nullable = false)
    private int soldCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean isFlashSale = false;

    @Column(precision = 12, scale = 0)
    private BigDecimal flashSalePrice;

    @Column(nullable = false)
    private int flashSaleLimit = 0;

    @Column(nullable = false)
    private int flashSaleSold = 0;

    @Column
    private LocalDateTime flashSaleStart;

    @Column
    private LocalDateTime flashSaleEnd;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getImagePath2() { return imagePath2; }
    public void setImagePath2(String imagePath2) { this.imagePath2 = imagePath2; }

    public String getImagePath3() { return imagePath3; }
    public void setImagePath3(String imagePath3) { this.imagePath3 = imagePath3; }

    public CategoryEntity getCategory() { return category; }
    public void setCategory(CategoryEntity category) { this.category = category; }

    public String getAgeRange() { return ageRange; }
    public void setAgeRange(String ageRange) { this.ageRange = ageRange; }

    public String getSizes() { return sizes; }
    public void setSizes(String sizes) { this.sizes = sizes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public int getSoldCount() { return soldCount; }
    public void setSoldCount(int soldCount) { this.soldCount = soldCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isFlashSale() { return isFlashSale; }
    public void setFlashSale(boolean flashSale) { isFlashSale = flashSale; }

    public BigDecimal getFlashSalePrice() { return flashSalePrice; }
    public void setFlashSalePrice(BigDecimal flashSalePrice) { this.flashSalePrice = flashSalePrice; }

    public int getFlashSaleLimit() { return flashSaleLimit; }
    public void setFlashSaleLimit(int flashSaleLimit) { this.flashSaleLimit = flashSaleLimit; }

    public int getFlashSaleSold() { return flashSaleSold; }
    public void setFlashSaleSold(int flashSaleSold) { this.flashSaleSold = flashSaleSold; }

    public LocalDateTime getFlashSaleStart() { return flashSaleStart; }
    public void setFlashSaleStart(LocalDateTime flashSaleStart) { this.flashSaleStart = flashSaleStart; }

    public LocalDateTime getFlashSaleEnd() { return flashSaleEnd; }
    public void setFlashSaleEnd(LocalDateTime flashSaleEnd) { this.flashSaleEnd = flashSaleEnd; }

    @Transient
    public String getFormattedPrice() {
        if (price == null) return "0đ";
        return String.format("%,.0f", price) + "đ";
    }

    @Transient
    public String getFormattedOriginalPrice() {
        if (originalPrice == null) return null;
        return String.format("%,.0f", originalPrice) + "đ";
    }

    @Transient
    public int getDiscountPercent() {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) == 0) return 0;
        if (price == null) return 0;
        return originalPrice.subtract(price).multiply(BigDecimal.valueOf(100)).divide(originalPrice, 0, java.math.RoundingMode.HALF_UP).intValue();
    }

    @Transient
    public boolean isFlashSaleActive() {
        if (!isFlashSale) return false;
        LocalDateTime now = LocalDateTime.now();
        if (flashSaleStart != null && now.isBefore(flashSaleStart)) return false;
        if (flashSaleEnd != null && !now.isBefore(flashSaleEnd)) return false;
        if (flashSaleSold >= flashSaleLimit && flashSaleLimit > 0) return false;
        return true;
    }

    @Transient
    public BigDecimal getActivePrice() {
        return isFlashSaleActive() && flashSalePrice != null ? flashSalePrice : price;
    }

    @Transient
    public String getFormattedActivePrice() {
        BigDecimal p = getActivePrice();
        if (p == null) return "0đ";
        return String.format("%,.0f", p) + "đ";
    }

    @Transient
    public int getActiveDiscountPercent() {
        BigDecimal base = originalPrice != null ? originalPrice : price;
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) return 0;
        BigDecimal p = getActivePrice();
        if (p == null) return 0;
        return base.subtract(p).multiply(BigDecimal.valueOf(100)).divide(base, 0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
