package com.legalflow.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventContractTest {

    private static final UUID FIRM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID LAWYER_SUB = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void envelopeSurvivesTheJsonRoundTripUsedOnTheWire() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        EventRecipient client = EventRecipient.client(CLIENT_ID, FIRM, "Client A", "client@example.test", "+15550100");
        EventRecipient lawyer = EventRecipient.lawyer(LAWYER_SUB, FIRM, "Lawyer A", "lawyer@example.test", null);
        DomainEventEnvelope event = DomainEventEnvelope.of(EventTypes.INVOICE_ISSUED, FIRM, "billing-service",
                "INVOICE", UUID.randomUUID(), Map.of("client", client, "actor", lawyer, "message", "Facture émise"));

        Message message = converter.toMessage(event, new MessageProperties());
        message.getMessageProperties().setInferredArgumentType(DomainEventEnvelope.class);
        DomainEventEnvelope received = (DomainEventEnvelope) converter.fromMessage(message);

        assertEquals(event.eventId(), received.eventId());
        assertEquals(event.eventType(), received.eventType());
        assertEquals(event.occurredAt().toEpochMilli(), received.occurredAt().toEpochMilli());
        assertEquals(FIRM, received.lawFirmId());
        assertEquals(event.aggregateId(), received.aggregateId());
        assertEquals(client, new ObjectMapper().convertValue(received.payload().get("client"), EventRecipient.class));
        assertEquals(lawyer, new ObjectMapper().convertValue(received.payload().get("actor"), EventRecipient.class));
        assertEquals("Facture émise", received.payload().get("message"));
    }

    @Test
    void recipientFactoriesApplyTheIdentityConvention() {
        EventRecipient client = EventRecipient.client(CLIENT_ID, FIRM, "C", null, null);
        EventRecipient lawyer = EventRecipient.lawyer(LAWYER_SUB, FIRM, "L", null, null);
        EventRecipient secretary = EventRecipient.secretary(LAWYER_SUB, FIRM, "S", null, null);

        assertEquals(EventRecipient.CLIENT, client.recipientRole());
        assertEquals(CLIENT_ID, client.recipientId());
        assertEquals(EventRecipient.LAWYER, lawyer.recipientRole());
        assertEquals(EventRecipient.SECRETARY, secretary.recipientRole());
    }

    @Test
    void envelopeFactoryRejectsMissingRequiredFields() {
        assertThrows(NullPointerException.class, () -> DomainEventEnvelope.of(EventTypes.CASE_CREATED, null,
                "case-service", "CASE", UUID.randomUUID(), Map.of()));
        assertThrows(NullPointerException.class, () -> DomainEventEnvelope.of(EventTypes.CASE_CREATED, FIRM,
                "case-service", "CASE", null, Map.of()));
    }

    @Test
    void eventTypesAreUniqueAndNotBlank() {
        assertEquals(EventTypes.ALL.size(), new HashSet<>(EventTypes.ALL).size());
        EventTypes.ALL.forEach(type -> assertFalse(type.isBlank()));
    }
}
