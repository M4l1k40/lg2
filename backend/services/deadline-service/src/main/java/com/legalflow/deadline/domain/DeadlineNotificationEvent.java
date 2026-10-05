package com.legalflow.deadline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deadline_notification_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_deadline_notification_event", columnNames = {"deadline_id", "event_key"})
})
public class DeadlineNotificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deadline_id", nullable = false)
    private UUID deadlineId;

    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected DeadlineNotificationEvent() {
    }

    public DeadlineNotificationEvent(UUID deadlineId, String eventKey) {
        this.deadlineId = deadlineId;
        this.eventKey = eventKey;
    }
}
