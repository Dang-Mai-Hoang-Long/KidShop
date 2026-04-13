package com.example.demo.service;

import com.example.demo.entity.AuditLogEntity;
import com.example.demo.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String username, String action, String details) {
        AuditLogEntity log = new AuditLogEntity();
        log.setUsername(username != null ? username : "SYSTEM");
        log.setAction(action);
        log.setDetails(details);
        auditLogRepository.save(log);
    }
    
    public List<AuditLogEntity> getAllLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<AuditLogEntity> getRecentLogs(int limit) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc().stream().limit(limit).toList();
    }

    public Page<AuditLogEntity> searchLogs(String keyword, String type, Pageable pageable) {
        List<AuditLogEntity> filtered = auditLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(log -> matches(log, keyword))
                .filter(log -> matchesType(log, type))
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<AuditLogEntity> pageContent = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    private boolean matches(AuditLogEntity log, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(log.getUsername(), normalized)
                || contains(log.getAction(), normalized)
                || contains(log.getDetails(), normalized)
                || contains(log.getTargetOrderCode(), normalized);
    }

    private boolean matchesType(AuditLogEntity log, String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) {
            return true;
        }

        boolean orderLog = isOrderLog(log);
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "ORDER" -> orderLog;
            case "ACTION" -> !orderLog;
            default -> true;
        };
    }

    private boolean isOrderLog(AuditLogEntity log) {
        return contains(log.getAction(), "đơn hàng")
                || contains(log.getAction(), "order")
                || contains(log.getDetails(), "đơn hàng")
                || contains(log.getDetails(), "order")
                || contains(log.getTargetOrderCode(), "order");
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
