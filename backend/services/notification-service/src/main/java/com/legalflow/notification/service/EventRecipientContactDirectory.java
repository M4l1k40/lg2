package com.legalflow.notification.service;

import com.legalflow.events.EventRecipient;
import com.legalflow.notification.integration.ClientDirectoryClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Part des coordonnées de l'événement et ne complète que ce qui manque, uniquement pour un CLIENT : le publisher
 * ne connaît que son {@code clientId}. Les valeurs de l'événement ont toujours priorité.
 */
@Component
public class EventRecipientContactDirectory implements RecipientContactDirectory {

    private final ClientDirectoryClient clientDirectoryClient;

    public EventRecipientContactDirectory(ClientDirectoryClient clientDirectoryClient) {
        this.clientDirectoryClient = clientDirectoryClient;
    }

    @Override
    public NotificationRecipientContact contactFor(EventRecipient recipient, UUID lawFirmId) {
        NotificationRecipientContact fromEvent = new NotificationRecipientContact(
                recipient.name(), recipient.email(), recipient.phone());
        boolean complete = hasText(recipient.email()) && hasText(recipient.phone());
        if (complete || !EventRecipient.CLIENT.equals(recipient.recipientRole())) {
            return fromEvent;
        }
        return clientDirectoryClient.findClientContact(recipient.recipientId(), lawFirmId)
                .map(found -> new NotificationRecipientContact(
                        firstText(fromEvent.name(), found.name()),
                        firstText(fromEvent.email(), found.email()),
                        firstText(fromEvent.phone(), found.phone())))
                .orElse(fromEvent);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstText(String preferred, String fallback) {
        return hasText(preferred) ? preferred : fallback;
    }
}
