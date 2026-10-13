package com.legalflow.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fige les noms de champs JSON lus par le {@code notification-service} : le renommage d'un champ casserait
 * la consommation sans erreur de compilation.
 */
class WireFormatTest {

    private static final UUID FIRM = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void envelopeKeepsTheFieldNamesExpectedByTheConsumer() {
        DomainEventEnvelope event = DomainEventEnvelope.of(EventTypes.CASE_CREATED, FIRM, "case-service", "CASE",
                UUID.randomUUID(), Map.of("message", "Nouveau dossier"));

        assertEquals(Set.of("eventId", "eventType", "occurredAt", "lawFirmId", "source", "aggregateType",
                "aggregateId", "payload"), fieldNames(mapper.valueToTree(event)));
    }

    @Test
    void recipientKeepsTheFieldNamesExpectedByTheConsumer() {
        EventRecipient recipient = EventRecipient.client(UUID.randomUUID(), FIRM, "Client A", "client@example.test", null);

        assertEquals(Set.of("recipientId", "lawFirmId", "recipientRole", "name", "email", "phone"),
                fieldNames(mapper.valueToTree(recipient)));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
