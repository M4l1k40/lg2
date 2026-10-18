package com.legalflow.user.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.legalflow.user.security.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Lit la fiche d'un client avec le token de l'utilisateur connecté : le client-service applique donc lui-même
 * l'isolation par cabinet et, pour un avocat, la limitation aux clients dont il est en charge.
 */
@Component
public class ClientRecordReader {

    private final RestClient restClient;

    public ClientRecordReader(@Qualifier("clientServiceRestClient") RestClient clientServiceRestClient) {
        this.restClient = clientServiceRestClient;
    }

    public ClientRecord read(UUID clientId) {
        JwtAuthenticationToken authentication = TenantContext.requireJwtAuthentication();
        try {
            ClientRecord record = restClient.get()
                    .uri("/clients/{clientId}", clientId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authentication.getToken().getTokenValue())
                    .retrieve()
                    .body(ClientRecord.class);
            if (record == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Client service returned an empty client record.");
            }
            return record;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "client not found in your law firm");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Client service could not return the client record.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Client service could not return the client record.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClientRecord(String firstName, String lastName, String email) {
    }
}
