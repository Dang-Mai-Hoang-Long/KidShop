package com.example.demo.repository;

import com.example.demo.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findAllByOrderByCreatedAtDesc();
    org.springframework.data.domain.Page<AuditLogEntity> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);
}
