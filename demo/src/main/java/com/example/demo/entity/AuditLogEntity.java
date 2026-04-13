package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username; // user performing action

    @Column(nullable = false)
    private String action; // e.g. "BUY", "EDIT_PRODUCT", "BAN_USER", "LOGIN"

    @Column(columnDefinition = "TEXT")
    private String details; // specific old/new values

    private LocalDateTime createdAt = LocalDateTime.now();

    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("(MWK\\d{16})");

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Transient
    public String getTargetOrderCode() {
        String source = (details != null && !details.isBlank()) ? details : action;
        if (source == null || source.isBlank()) {
            return null;
        }

        Matcher matcher = ORDER_CODE_PATTERN.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }
}
