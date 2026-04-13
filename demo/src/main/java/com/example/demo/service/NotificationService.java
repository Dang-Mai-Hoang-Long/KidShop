package com.example.demo.service;

import com.example.demo.entity.NotificationEntity;
import com.example.demo.entity.UserAccountEntity;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;

    public NotificationService(NotificationRepository notificationRepository, UserAccountRepository userAccountRepository) {
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationEntity> getRecentNotifications(Long recipientUserId, int limit) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, PageRequest.of(0, Math.max(limit, 1), Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    @Transactional(readOnly = true)
    public Page<NotificationEntity> getNotificationPage(Long recipientUserId, Pageable pageable) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, pageable);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long recipientUserId) {
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
    }

    public void notifyUserAndAdmins(Long userId, String title, String message, String targetUrl, Long targetOrderId, String notificationType) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        recipientIds.add(userId);
        recipientIds.addAll(findAdminIds());
        createNotifications(recipientIds, title, message, targetUrl, targetOrderId, notificationType);
    }

    public void notifyUserAndActor(Long userId, Long actorUserId, String title, String message, String targetUrl, Long targetOrderId, String notificationType) {
        Set<Long> recipientIds = new LinkedHashSet<>();
        recipientIds.add(userId);
        if (actorUserId != null) {
            recipientIds.add(actorUserId);
        }
        createNotifications(recipientIds, title, message, targetUrl, targetOrderId, notificationType);
    }

    public void markAsRead(Long notificationId, Long recipientUserId) {
        notificationRepository.findByIdAndRecipientUserId(notificationId, recipientUserId).ifPresent(notification -> {
            if (notification.getReadAt() == null) {
                notification.setReadAt(LocalDateTime.now());
                notificationRepository.save(notification);
            }
        });
    }

    public NotificationEntity markAsReadAndReturn(Long notificationId, Long recipientUserId) {
        NotificationEntity notification = notificationRepository.findByIdAndRecipientUserId(notificationId, recipientUserId).orElseThrow();
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return notification;
    }

    public void markAllAsRead(Long recipientUserId) {
        notificationRepository.markAllRead(recipientUserId, LocalDateTime.now());
    }

    public void deleteSelected(Long recipientUserId, Collection<Long> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        List<NotificationEntity> selectedNotifications = notificationRepository.findByRecipientUserIdAndIdIn(recipientUserId, notificationIds);
        notificationRepository.deleteAll(selectedNotifications);
    }

    public void deleteAll(Long recipientUserId) {
        notificationRepository.deleteAllByRecipientUserId(recipientUserId);
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void purgeExpiredNotifications() {
        notificationRepository.deleteExpired(LocalDateTime.now().minusDays(30));
    }

    private void createNotifications(Collection<Long> recipientIds, String title, String message, String targetUrl, Long targetOrderId, String notificationType) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            return;
        }

        List<NotificationEntity> notifications = new ArrayList<>();
        for (Long recipientId : recipientIds) {
            if (recipientId == null) {
                continue;
            }

            UserAccountEntity recipient = userAccountRepository.findById(recipientId).orElse(null);
            if (recipient == null) {
                continue;
            }

            NotificationEntity notification = new NotificationEntity();
            notification.setRecipientUserId(recipient.getId());
            notification.setRecipientAccount(recipient.getAccount());
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setTargetUrl(targetUrl);
            notification.setTargetOrderId(targetOrderId);
            notification.setNotificationType(notificationType);
            notifications.add(notification);
        }

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    private List<Long> findAdminIds() {
        return userAccountRepository.findAllByRoleIgnoreCase("ADMIN").stream().map(UserAccountEntity::getId).toList();
    }
}