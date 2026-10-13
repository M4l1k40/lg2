package com.legalflow.events;

import java.util.UUID;

/**
 * Destinataire d'une notification.
 *
 * <p><b>Convention (SEC-07)</b> : {@code recipientId} est le {@code clientId} (fiche client) quand le rôle est
 * {@code CLIENT}, et le {@code sub} Keycloak de l'utilisateur pour le personnel ({@code LAWYER},
 * {@code SECRETARY}). Utiliser les fabriques ci-dessous plutôt que le constructeur : elles rendent la
 * convention impossible à oublier.
 */
public record EventRecipient(
        UUID recipientId,
        UUID lawFirmId,
        String recipientRole,
        String name,
        String email,
        String phone) {

    public static final String CLIENT = "CLIENT";
    public static final String LAWYER = "LAWYER";
    public static final String SECRETARY = "SECRETARY";

    /** @param clientId identifiant de la fiche client (claim {@code clientId}), pas le compte Keycloak */
    public static EventRecipient client(UUID clientId, UUID lawFirmId, String name, String email, String phone) {
        return new EventRecipient(clientId, lawFirmId, CLIENT, name, email, phone);
    }

    /** @param keycloakSub identifiant Keycloak (claim {@code sub}) de l'avocat */
    public static EventRecipient lawyer(UUID keycloakSub, UUID lawFirmId, String name, String email, String phone) {
        return new EventRecipient(keycloakSub, lawFirmId, LAWYER, name, email, phone);
    }

    /** @param keycloakSub identifiant Keycloak (claim {@code sub}) du secrétaire */
    public static EventRecipient secretary(UUID keycloakSub, UUID lawFirmId, String name, String email, String phone) {
        return new EventRecipient(keycloakSub, lawFirmId, SECRETARY, name, email, phone);
    }
}
