package com.legalflow.notification.service;

import com.legalflow.events.EventRecipient;

import java.util.UUID;

/** Détermine les coordonnées à utiliser pour envoyer la notification d'un destinataire d'événement. */
@FunctionalInterface
public interface RecipientContactDirectory {

    NotificationRecipientContact contactFor(EventRecipient recipient, UUID lawFirmId);
}
