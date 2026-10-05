package com.legalflow.notification.service;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.dto.NotificationCreateRequest;
import com.legalflow.notification.dto.NotificationResponse;
import com.legalflow.notification.dto.ReadAllResponse;
import com.legalflow.notification.repository.NotificationRepository;
import com.legalflow.notification.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRoutingService notificationRoutingService;
    private final NotificationRecipientContactResolver recipientContactResolver;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationRoutingService notificationRoutingService,
                               NotificationRecipientContactResolver recipientContactResolver) {
        this.notificationRepository = notificationRepository;
        this.notificationRoutingService = notificationRoutingService;
        this.recipientContactResolver = recipientContactResolver;
    }

    public NotificationResponse create(NotificationCreateRequest request) {
        NotificationRecipientType recipientType = notificationRoutingService.recipientTypeForCurrentRecipient();
        NotificationChannel channel = notificationRoutingService.channelFor(recipientType);
        Notification notification = new Notification(
                TenantContext.requireLawFirmId(),
                TenantContext.requireRecipientId(),
                recipientType,
                channel,
                request.getSubject() == null || request.getSubject().isBlank() ? request.getTitle() : request.getSubject(),
                request.getTitle(),
                request.getMessage(),
                request.getType(),
                request.getReferenceType(),
                request.getReferenceId());
        notification = notificationRepository.saveAndFlush(notification);
        if (recipientType == null) {
            notification.markFailed("No delivery rule is defined for the ADMIN role.");
        } else {
            try {
                notificationRoutingService.send(notification, recipientContactResolver.resolve(recipientType));
                notification.markSent(LocalDateTime.now());
            } catch (NotificationDeliveryException exception) {
                notification.markFailed(exception.getMessage());
            } catch (RuntimeException exception) {
                notification.markFailed("Notification provider failed to send notification.");
            }
        }
        return NotificationResponse.from(notificationRepository.saveAndFlush(notification));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAll() {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        UUID recipientId = TenantContext.requireRecipientId();
        return notificationRepository.findAllByLawFirmIdAndRecipientIdOrderByCreatedAtDesc(lawFirmId, recipientId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnread() {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        UUID recipientId = TenantContext.requireRecipientId();
        return notificationRepository
                .findAllByLawFirmIdAndRecipientIdAndReadFalseOrderByCreatedAtDesc(lawFirmId, recipientId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(UUID id) {
        return NotificationResponse.from(findOwnedNotification(id));
    }

    @Transactional(readOnly = true)
    public NotificationResponse getStatus(UUID id) {
        return NotificationResponse.from(findOwnedNotification(id));
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {
        Notification notification = findOwnedNotification(id);
        if (!notification.isRead()) {
            notification.markRead(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public ReadAllResponse markAllRead() {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        UUID recipientId = TenantContext.requireRecipientId();
        List<Notification> unread = notificationRepository
                .findAllByLawFirmIdAndRecipientIdAndReadFalse(lawFirmId, recipientId);
        if (unread.isEmpty()) {
            return new ReadAllResponse(0);
        }
        LocalDateTime readAt = LocalDateTime.now();
        unread.forEach(notification -> notification.markRead(readAt));
        notificationRepository.saveAll(unread);
        return new ReadAllResponse(unread.size());
    }

    @Transactional
    public void delete(UUID id) {
        notificationRepository.delete(findOwnedNotification(id));
    }

    private Notification findOwnedNotification(UUID id) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        UUID recipientId = TenantContext.requireRecipientId();
        return notificationRepository.findByIdAndLawFirmIdAndRecipientId(id, lawFirmId, recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification not found: " + id));
    }
}
