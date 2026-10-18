package com.legalflow.user.service;

import com.legalflow.user.dto.ClientInvitationResponse;
import com.legalflow.user.integration.ClientRecordReader;
import com.legalflow.user.integration.ClientRecordReader.ClientRecord;
import com.legalflow.user.keycloak.KeycloakAdminClient;
import com.legalflow.user.keycloak.KeycloakUser;
import com.legalflow.user.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Invitation d'un client à se connecter : aucun mot de passe ne circule. Keycloak envoie à l'adresse de la fiche un
 * lien à durée limitée qui permet au client de choisir lui-même son mot de passe.
 */
@Service
public class ClientInvitationService {

    static final List<String> REQUIRED_ACTIONS = List.of("UPDATE_PASSWORD");
    static final int LINK_LIFESPAN_SECONDS = 72 * 3600;

    private static final Logger log = LoggerFactory.getLogger(ClientInvitationService.class);

    private final KeycloakAdminClient keycloak;
    private final ClientRecordReader clientRecordReader;
    private final String frontendRedirectUri;
    private final String frontendClientId;

    public ClientInvitationService(KeycloakAdminClient keycloak,
                                   ClientRecordReader clientRecordReader,
                                   @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                                   @Value("${app.frontend-client-id:legalflow-frontend}") String frontendClientId) {
        this.keycloak = keycloak;
        this.clientRecordReader = clientRecordReader;
        this.frontendRedirectUri = frontendUrl.endsWith("/") ? frontendUrl : frontendUrl + "/";
        this.frontendClientId = frontendClientId;
    }

    public ClientInvitationResponse invite(UUID clientId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        // La fiche est lue avec le token de l'appelant : cabinet et périmètre de l'avocat sont contrôlés par le client-service.
        ClientRecord record = clientRecordReader.read(clientId);
        String email = record.email() == null ? "" : record.email().trim();
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The client record has no email address.");
        }

        Optional<KeycloakUser> linked = keycloak.findUserByClientId(clientId);
        String userId;
        String targetEmail;
        boolean created = false;

        if (linked.isPresent()) {
            KeycloakUser user = linked.get();
            if (!lawFirmId.toString().equalsIgnoreCase(user.attribute("lawFirmId"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The account linked to this client is not in your law firm.");
            }
            if (Boolean.FALSE.equals(user.enabled())) {
                // Une invitation ne doit pas contourner une désactivation décidée par l'administration.
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The client account is disabled.");
            }
            userId = user.id();
            targetEmail = user.email() == null || user.email().isBlank() ? email : user.email();
        } else {
            if (keycloak.findUserByEmail(email).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already uses this email address.");
            }
            Map<String, List<String>> attributes = new LinkedHashMap<>();
            attributes.put("lawFirmId", List.of(lawFirmId.toString()));
            attributes.put("clientId", List.of(clientId.toString()));

            userId = keycloak.createInvitedUser(email.toLowerCase(Locale.ROOT), email,
                    textOrDash(record.firstName()), textOrDash(record.lastName()), attributes);
            created = true;
            try {
                keycloak.addRealmRole(userId, "CLIENT");
            } catch (RuntimeException exception) {
                removeQuietly(userId);
                throw exception;
            }
            targetEmail = email;
        }

        try {
            keycloak.sendActionsEmail(userId, REQUIRED_ACTIONS, LINK_LIFESPAN_SECONDS, frontendClientId, frontendRedirectUri);
        } catch (RuntimeException exception) {
            if (created) {
                // Pas de compte orphelin : l'invitation pourra être relancée proprement.
                removeQuietly(userId);
            }
            throw exception;
        }

        log.info("Client invitation sent for client {} in law firm {} ({})", clientId, lawFirmId,
                created ? "account created" : "account existed");
        return new ClientInvitationResponse(created ? "INVITED" : "REINVITED", targetEmail);
    }

    private void removeQuietly(String userId) {
        try {
            keycloak.deleteUser(userId);
        } catch (RuntimeException ignored) {
            // meilleur effort
        }
    }

    private static String textOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
