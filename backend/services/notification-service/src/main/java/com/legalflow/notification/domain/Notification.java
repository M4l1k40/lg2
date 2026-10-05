package com.legalflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_law_firm_id", columnList = "law_firm_id"),
        @Index(name = "idx_notifications_tenant_recipient", columnList = "law_firm_id,recipient_id"),
        @Index(name = "idx_notifications_tenant_recipient_read", columnList = "law_firm_id,recipient_id,is_read"),
        @Index(name = "idx_notifications_tenant_recipient_created", columnList = "law_firm_id,recipient_id,created_at"),
        @Index(name = "idx_notifications_event_id", columnList = "event_id"),
        @Index(name = "uk_notifications_event_recipient_channel",
            columnList = "event_id,law_firm_id,recipient_id,channel", unique = true)
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "law_firm_id", nullable = false)
    private UUID lawFirmId;

    @NotNull
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "event_id")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", length = 24)
    private NotificationRecipientType recipientType;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private NotificationChannel channel;

    @Column(length = 160)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private NotificationStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private NotificationType type;

    @NotBlank
    @Column(nullable = false, length = 160)
    private String title;

    @NotBlank
    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "reference_type", length = 64)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected Notification() {
    }

    public Notification(UUID lawFirmId, UUID recipientId, NotificationType type, String title,
                        String message, String referenceType, UUID referenceId) {
        this(lawFirmId, recipientId, null, null, title, title, message, type, referenceType, referenceId);
    }

    public Notification(UUID lawFirmId, UUID recipientId, NotificationRecipientType recipientType,
                        NotificationChannel channel, String subject, String title, String message,
                        NotificationType type, String referenceType, UUID referenceId) {
        this(null, lawFirmId, recipientId, recipientType, channel, subject, title, message,
            type, referenceType, referenceId);
        }

        public Notification(UUID eventId, UUID lawFirmId, UUID recipientId, NotificationRecipientType recipientType,
                NotificationChannel channel, String subject, String title, String message,
                NotificationType type, String referenceType, UUID referenceId) {
        this.lawFirmId = lawFirmId;
        this.recipientId = recipientId;
        this.eventId = eventId;
        this.recipientType = recipientType;
        this.channel = channel;
        this.subject = subject;
        this.type = type;
        this.title = title;
        this.message = message;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.read = false;
        this.status = NotificationStatus.PENDING;
    }

    public void markRead(LocalDateTime readAt) {
        if (!read) {
            this.read = true;
            this.readAt = readAt;
            if (channel == null || channel == NotificationChannel.IN_APP) {
                this.status = NotificationStatus.READ;
            }
        }
    }

    public void markPending() {
        this.status = NotificationStatus.PENDING;
        this.sentAt = null;
        this.errorMessage = null;
    }

    public void markSent(LocalDateTime sentAt) {
        this.status = NotificationStatus.SENT;
        this.sentAt = sentAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.sentAt = null;
        this.errorMessage = errorMessage;
    }

    public UUID getId() { return id; }
    public UUID getLawFirmId() { return lawFirmId; }
    public UUID getRecipientId() { return recipientId; }
    public UUID getEventId() { return eventId; }
    public NotificationRecipientType getRecipientType() { return recipientType; }
    public NotificationChannel getChannel() { return channel; }
    public String getSubject() { return subject; }
    public NotificationStatus getStatus() { return status; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getReadAt() { return readAt; }
}
