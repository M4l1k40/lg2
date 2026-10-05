package com.legalflow.notification.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DomainEventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID lawFirmId,
        String source,
        String aggregateType,
        UUID aggregateId,
        Map<String, Object> payload) {
}