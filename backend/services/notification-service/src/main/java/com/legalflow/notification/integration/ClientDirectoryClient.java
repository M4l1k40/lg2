package com.legalflow.notification.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.legalflow.notification.service.NotificationRecipientContact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;
import java.util.UUID;

/** Retrouve les coordonnées d'un client auprès du client-service avec le compte de service. */
@Component
public class ClientDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(ClientDirectoryClient.class);

    private final RestClient clientServiceRestClient;
    private final ServiceAccountTokenProvider tokenProvider;

    public ClientDirectoryClient(@Qualifier("clientServiceRestClient") RestClient clientServiceRestClient,
                                 ServiceAccountTokenProvider tokenProvider) {
        this.clientServiceRestClient = clientServiceRestClient;
        this.tokenProvider = tokenProvider;
    }

    public Optional<NotificationRecipientContact> findClientContact(UUID clientId, UUID lawFirmId) {
        Optional<String> token = tokenProvider.accessToken();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        try {
            ClientContact contact = clientServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/clients/{id}/contact")
                            .queryParam("lawFirmId", lawFirmId)
                            .build(clientId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.get())
                    .retrieve()
                    .body(ClientContact.class);
            if (contact == null) {
                return Optional.empty();
            }
            return Optional.of(new NotificationRecipientContact(
                    joinedName(contact.firstName(), contact.lastName()), contact.email(), contact.phone()));
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                tokenProvider.invalidate();
            }
            log.warn("Client contact lookup refused with status {} for client {}", status, clientId);
            return Optional.empty();
        } catch (RestClientException exception) {
            log.warn("Client contact lookup failed for client {}: {}", clientId, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String joinedName(String firstName, String lastName) {
        boolean noFirst = firstName == null || firstName.isBlank();
        boolean noLast = lastName == null || lastName.isBlank();
        if (noFirst && noLast) {
            return null;
        }
        if (noFirst) {
            return lastName.trim();
        }
        return noLast ? firstName.trim() : firstName.trim() + " " + lastName.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClientContact(String firstName, String lastName, String email, String phone) {
    }
}
