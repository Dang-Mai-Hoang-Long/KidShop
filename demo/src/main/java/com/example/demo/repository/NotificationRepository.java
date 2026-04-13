package com.example.demo.repository;

import com.example.demo.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findTop10ByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    Page<NotificationEntity> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    Optional<NotificationEntity> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    List<NotificationEntity> findByRecipientUserIdAndIdIn(Long recipientUserId, Collection<Long> ids);

    @Modifying
    @Query("delete from NotificationEntity n where n.recipientUserId = :recipientUserId")
    void deleteAllByRecipientUserId(@Param("recipientUserId") Long recipientUserId);

    @Modifying
    @Query("delete from NotificationEntity n where n.createdAt < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("update NotificationEntity n set n.readAt = :readAt where n.recipientUserId = :recipientUserId and n.readAt is null")
    int markAllRead(@Param("recipientUserId") Long recipientUserId, @Param("readAt") LocalDateTime readAt);
}