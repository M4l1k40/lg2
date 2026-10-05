package com.legalflow.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "law_firm_id", nullable = false, updatable = false)
    private UUID lawFirmId;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(nullable = false, length = 16)
    private String status;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, UUID lawFirmId, String eventType) {
        this.eventId = eventId;
        this.lawFirmId = lawFirmId;
        this.eventType = eventType;
        this.status = "PROCESSED";
    }

    public UUID getEventId() { return eventId; }
    public UUID getLawFirmId() { return lawFirmId; }
    public String getEventType() { return eventType; }
    public String getStatus() { return status; }
    public Instant getProcessedAt() { return processedAt; }
}