package com.legalflow.events;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Enveloppe commune de tous les événements publiés sur l'exchange {@link EventsTopology#EVENTS_EXCHANGE}.
 *
 * <p>Le {@code payload} contient les destinataires (clés {@code actor}, {@code lawyer}, {@code client},
 * {@code creator}) sous forme d'{@link EventRecipient}, plus un {@code message} optionnel.
 */
public record DomainEventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID lawFirmId,
        String source,
        String aggregateType,
        UUID aggregateId,
        Map<String, Object> payload) {

    /** Crée un événement neuf (identifiant et date générés) en refusant tout champ obligatoire manquant. */
    public static DomainEventEnvelope of(String eventType, UUID lawFirmId, String source,
                                         String aggregateType, UUID aggregateId,
                                         Map<String, Object> payload) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(lawFirmId, "lawFirmId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payload, "payload");
        return new DomainEventEnvelope(UUID.randomUUID(), eventType, Instant.now(), lawFirmId, source,
                aggregateType, aggregateId, payload);
    }
}
