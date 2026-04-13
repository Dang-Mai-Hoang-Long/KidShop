package com.example.demo.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    @Column(nullable = false, length = 100)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 500)
    private String shippingAddress;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 30)
    private String status = "PENDING";

    @Column(nullable = false, length = 30)
    private String paymentMethod = "COD";

    @Column(nullable = false)
    private boolean paymentConfirmed;

    private LocalDateTime paymentConfirmedAt;

    @Column(length = 60)
    private String paymentGateway;

    @Column(length = 120)
    private String paymentReferenceCode;

    @Column(unique = true)
    private Long paymentTransactionId;

    @Column(precision = 14, scale = 0)
    private BigDecimal paymentTransferAmount;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItemEntity> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public UserAccountEntity getUser() { return user; }
    public void setUser(UserAccountEntity user) { this.user = user; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public boolean isPaymentConfirmed() { return paymentConfirmed; }
    public void setPaymentConfirmed(boolean paymentConfirmed) { this.paymentConfirmed = paymentConfirmed; }

    public LocalDateTime getPaymentConfirmedAt() { return paymentConfirmedAt; }
    public void setPaymentConfirmedAt(LocalDateTime paymentConfirmedAt) { this.paymentConfirmedAt = paymentConfirmedAt; }

    public String getPaymentGateway() { return paymentGateway; }
    public void setPaymentGateway(String paymentGateway) { this.paymentGateway = paymentGateway; }

    public String getPaymentReferenceCode() { return paymentReferenceCode; }
    public void setPaymentReferenceCode(String paymentReferenceCode) { this.paymentReferenceCode = paymentReferenceCode; }

    public Long getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(Long paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }

    public BigDecimal getPaymentTransferAmount() { return paymentTransferAmount; }
    public void setPaymentTransferAmount(BigDecimal paymentTransferAmount) { this.paymentTransferAmount = paymentTransferAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<OrderItemEntity> getItems() { return items; }
    public void setItems(List<OrderItemEntity> items) { this.items = items; }

    @Transient
    public String getFormattedTotal() {
        return String.format("%,.0f", totalAmount) + "đ";
    }

    @Transient
    public String getStatusLabel() {
        return switch (status) {
            case "WAITING_PAYMENT" -> "Đang chờ thanh toán";
            case "PENDING" -> "Chờ xác nhận";
            case "CONFIRMED" -> "Đã xác nhận";
            case "SHIPPING" -> "Đang giao";
            case "DELIVERED" -> "Đã giao";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    @Transient
    public String getStatusColor() {
        return switch (status) {
            case "WAITING_PAYMENT" -> "secondary";
            case "PENDING" -> "warning";
            case "CONFIRMED" -> "info";
            case "SHIPPING" -> "primary";
            case "DELIVERED" -> "success";
            case "CANCELLED" -> "danger";
            default -> "secondary";
        };
    }

    @Transient
    public boolean isFinalStatus() {
        return "DELIVERED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status);
    }

    @Transient
    public boolean isWaitingPaymentStatus() {
        return "WAITING_PAYMENT".equalsIgnoreCase(status);
    }

    @Transient
    public boolean isBankTransfer() {
        return "BANK".equalsIgnoreCase(paymentMethod);
    }

    @Transient
    public String getPaymentStatusLabel() {
        if (!isBankTransfer()) {
            return "Thanh toán khi nhận hàng";
        }
        return paymentConfirmed ? "Đã thanh toán" : "Chưa thanh toán";
    }

    @Transient
    public boolean isAdminUpdateLocked() {
        return isFinalStatus() || isWaitingPaymentStatus();
    }
}
