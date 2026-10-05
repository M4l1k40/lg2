package com.legalflow.consultation.integration;

import com.legalflow.consultation.security.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class ClientServiceOwnershipVerifier {

    private final RestClient restClient;

    public ClientServiceOwnershipVerifier(RestClient clientServiceRestClient) {
        this.restClient = clientServiceRestClient;
    }

    public void verifyClientBelongsToCurrentTenant(UUID clientId) {
        JwtAuthenticationToken authentication = TenantContext.requireJwtAuthentication();
        try {
            restClient.get()
                    .uri("/clients/{clientId}", clientId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authentication.getToken().getTokenValue())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "client not found in your law firm");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Client service could not verify consultation ownership.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Client service could not verify consultation ownership.");
        }
    }
}
