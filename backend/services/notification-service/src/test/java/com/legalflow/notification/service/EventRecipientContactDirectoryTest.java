package com.legalflow.notification.service;

import com.legalflow.events.EventRecipient;
import com.legalflow.notification.integration.ClientDirectoryClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventRecipientContactDirectoryTest {

    private static final UUID FIRM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LAWYER_SUB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final ClientDirectoryClient clientDirectoryClient = mock(ClientDirectoryClient.class);
    private final EventRecipientContactDirectory directory = new EventRecipientContactDirectory(clientDirectoryClient);

    @Test
    void staffContactComesFromTheEventOnly() {
        EventRecipient lawyer = EventRecipient.lawyer(LAWYER_SUB, FIRM, "Maître A", null, null);

        NotificationRecipientContact contact = directory.contactFor(lawyer, FIRM);

        assertEquals(new NotificationRecipientContact("Maître A", null, null), contact);
        verify(clientDirectoryClient, never()).findClientContact(any(), any());
    }

    @Test
    void completeClientContactIsNotLookedUp() {
        EventRecipient client = EventRecipient.client(CLIENT_ID, FIRM, "Client A", "client@example.test", "555-0100");

        NotificationRecipientContact contact = directory.contactFor(client, FIRM);

        assertEquals(new NotificationRecipientContact("Client A", "client@example.test", "555-0100"), contact);
        verify(clientDirectoryClient, never()).findClientContact(any(), any());
    }

    @Test
    void missingClientDetailsAreCompletedWithoutOverridingTheEvent() {
        EventRecipient client = EventRecipient.client(CLIENT_ID, FIRM, null, "event@example.test", null);
        when(clientDirectoryClient.findClientContact(CLIENT_ID, FIRM)).thenReturn(Optional.of(
                new NotificationRecipientContact("Client A", "fiche@example.test", "555-0100")));

        NotificationRecipientContact contact = directory.contactFor(client, FIRM);

        assertEquals(new NotificationRecipientContact("Client A", "event@example.test", "555-0100"), contact);
    }

    @Test
    void failedLookupFallsBackToTheEventValues() {
        EventRecipient client = EventRecipient.client(CLIENT_ID, FIRM, "Client A", null, null);
        when(clientDirectoryClient.findClientContact(CLIENT_ID, FIRM)).thenReturn(Optional.empty());

        NotificationRecipientContact contact = directory.contactFor(client, FIRM);

        assertEquals(new NotificationRecipientContact("Client A", null, null), contact);
    }
}
