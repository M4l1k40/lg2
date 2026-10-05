package com.legalflow.notification.dto;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.domain.NotificationStatus;
import com.legalflow.notification.domain.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID eventId,
        UUID lawFirmId,
        UUID recipientId,
        NotificationRecipientType recipientType,
        NotificationRecipientType recipientRole,
        NotificationChannel channel,
        String subject,
        NotificationStatus status,
        NotificationType type,
        NotificationType eventType,
        String title,
        String message,
        String referenceType,
        UUID referenceId,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime sentAt,
        String errorMessage,
        LocalDateTime readAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEventId(),
                notification.getLawFirmId(),
                notification.getRecipientId(),
                notification.getRecipientType(),
                notification.getRecipientType(),
                notification.getChannel(),
                notification.getSubject(),
                notification.getStatus(),
                notification.getType(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getSentAt(),
                notification.getErrorMessage(),
                notification.getReadAt());
    }
}
