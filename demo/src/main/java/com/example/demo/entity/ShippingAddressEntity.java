package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipping_addresses")
public class ShippingAddressEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    @Column(length = 120)
    private String recipientName;

    @Column(length = 20)
    private String recipientPhone;

    @Column(columnDefinition = "TEXT")
    private String addressLine;

    @Column(length = 255)
    private String label;

    @Column(nullable = false)
    private boolean defaultAddress;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserAccountEntity getUser() { return user; }
    public void setUser(UserAccountEntity user) { this.user = user; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean isDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Transient
    public String getDisplayText() {
        StringBuilder builder = new StringBuilder();
        if (label != null && !label.isBlank()) {
            builder.append(label.trim()).append(" - ");
        }
        if (recipientName != null && !recipientName.isBlank()) {
            builder.append(recipientName.trim()).append(" | ");
        }
        if (recipientPhone != null && !recipientPhone.isBlank()) {
            builder.append(recipientPhone.trim()).append(" | ");
        }
        if (addressLine != null) {
            builder.append(addressLine.trim());
        }
        return builder.toString();
    }
}