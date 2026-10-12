package com.legalflow.case_service.integration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;
import java.util.UUID;

@Component
public class ClientServiceOwnershipVerifier {

    private final RestClient restClient;

    public ClientServiceOwnershipVerifier(RestClient clientServiceRestClient) {
        this.restClient = clientServiceRestClient;
    }

    public void verifyClientBelongsToCurrentTenant(UUID clientId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing authenticated JWT.");
        }

        try {
            restClient.get()
                    .uri("/clients/{clientId}", clientId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuthentication.getToken().getTokenValue())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "client not found in your law firm");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Client service could not verify ownership; case was not created.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Client service could not verify ownership; case was not created.");
        }
    }

    /**
     * Vérifie que l'avocat assigné à un dossier est bien en charge du client (liste lawyerIds du client-service).
     * Un client non visible pour l'appelant donne une erreur 422, comme verifyClientBelongsToCurrentTenant.
     */
    public void verifyLawyerInChargeOfClient(UUID clientId, UUID lawyerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing authenticated JWT.");
        }
        ClientAccess client;
        try {
            client = restClient.get()
                    .uri("/clients/{clientId}", clientId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuthentication.getToken().getTokenValue())
                    .retrieve()
                    .body(ClientAccess.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "client not found in your law firm");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Client service could not verify ownership; case was not created.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Client service could not verify ownership; case was not created.");
        }
        if (client == null || client.lawyerIds() == null || !client.lawyerIds().contains(lawyerId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The assigned lawyer is not in charge of this client. Assign the lawyer to the client first.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClientAccess(Set<UUID> lawyerIds) {
    }
}
