package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "app_users")
public class UserAccountEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String account;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 120)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "user_role", nullable = false, length = 20)
    private String role = "USER";

    @Column(name = "user_rank", nullable = false, length = 20)
    private String rank = "BRONZE";

    @Column(length = 20)
    private String gender = "OTHER";

    @Column(length = 255)
    private String avatarPath;

    @Column(length = 120)
    private String googleSub;

    @Column(nullable = false)
    private boolean gmailVerified = false;

    private LocalDateTime gmailVerifiedAt;

    @Column(length = 500)
    private String googleAvatarUrl;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal purchaseSpendOffset = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private LocalDateTime lockedUntil;

    @Column(length = 255)
    private String lockReason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public void setGoogleSub(String googleSub) {
        this.googleSub = googleSub;
    }

    public boolean isGmailVerified() {
        return gmailVerified;
    }

    public void setGmailVerified(boolean gmailVerified) {
        this.gmailVerified = gmailVerified;
    }

    public LocalDateTime getGmailVerifiedAt() {
        return gmailVerifiedAt;
    }

    public void setGmailVerifiedAt(LocalDateTime gmailVerifiedAt) {
        this.gmailVerifiedAt = gmailVerifiedAt;
    }

    public String getGoogleAvatarUrl() {
        return googleAvatarUrl;
    }

    public void setGoogleAvatarUrl(String googleAvatarUrl) {
        this.googleAvatarUrl = googleAvatarUrl;
    }

    public BigDecimal getPurchaseSpendOffset() {
        return purchaseSpendOffset == null ? BigDecimal.ZERO : purchaseSpendOffset;
    }

    public void setPurchaseSpendOffset(BigDecimal purchaseSpendOffset) {
        this.purchaseSpendOffset = purchaseSpendOffset == null ? BigDecimal.ZERO : purchaseSpendOffset;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    @Transient
    public boolean isLocked() {
        return !enabled || (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now()));
    }

    @Transient
    public String getLockLabel() {
        if (!enabled && lockedUntil == null) {
            return "Khóa vĩnh viễn";
        }

        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            return "Khóa đến " + lockedUntil.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
        }

        return enabled ? "Hoạt động" : "Đã khóa";
    }

    @Transient
    public String getRankLabel() {
        if (rank == null) {
            return "Đồng";
        }

        return switch (rank.toUpperCase()) {
            case "DIAMOND" -> "Kim cương";
            case "GOLD" -> "Vàng";
            case "SILVER" -> "Bạc";
            default -> "Đồng";
        };
    }

    public String getLockReason() {
        return lockReason;
    }

    public void setLockReason(String lockReason) {
        this.lockReason = lockReason;
    }

    @Transient
    public boolean isGoogleLinked() {
        return googleSub != null && !googleSub.isBlank();
    }

    @Transient
    public String getGmailStatusLabel() {
        return gmailVerified ? "Đã xác minh" : "Chưa xác minh";
    }
}