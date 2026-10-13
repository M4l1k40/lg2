package com.legalflow.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalflow.events.DomainEventEnvelope;
import com.legalflow.events.EventRecipient;
import com.legalflow.events.EventTypes;
import com.legalflow.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Garantit que tout type publié par la bibliothèque partagée est compris par le consommateur. */
class NotificationEventMapperContractTest {

    private static final UUID FIRM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID LAWYER_SUB = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final NotificationEventMapper mapper = new NotificationEventMapper(new ObjectMapper());

    @Test
    void everyPublishedEventTypeIsUnderstoodByTheConsumer() {
        for (String type : EventTypes.ALL) {
            DomainEventEnvelope event = DomainEventEnvelope.of(type, FIRM, "contract-test", "AGGREGATE",
                    UUID.randomUUID(), Map.of(
                            "actor", EventRecipient.lawyer(LAWYER_SUB, FIRM, "Lawyer A", "lawyer@example.test", null),
                            "lawyer", EventRecipient.lawyer(LAWYER_SUB, FIRM, "Lawyer A", "lawyer@example.test", null),
                            "creator", EventRecipient.secretary(LAWYER_SUB, FIRM, "Secretary A", null, null),
                            "client", EventRecipient.client(CLIENT_ID, FIRM, "Client A", "client@example.test", null)));

            MappedEventNotification mapped = assertDoesNotThrow(() -> mapper.map(event), type);

            assertEquals(NotificationType.valueOf(type), mapped.type(), type);
            assertFalse(mapped.recipients().isEmpty(), type);
        }
    }

    @Test
    void clientRecipientKeepsTheClientIdAsRecipientId() {
        DomainEventEnvelope event = DomainEventEnvelope.of(EventTypes.INVOICE_ISSUED, FIRM, "contract-test",
                "INVOICE", UUID.randomUUID(), Map.of(
                        "client", EventRecipient.client(CLIENT_ID, FIRM, "Client A", "client@example.test", null)));

        EventRecipient recipient = mapper.map(event).recipients().stream()
                .filter(r -> EventRecipient.CLIENT.equals(r.recipientRole()))
                .findFirst().orElseThrow();

        assertEquals(CLIENT_ID, recipient.recipientId());
    }
}
