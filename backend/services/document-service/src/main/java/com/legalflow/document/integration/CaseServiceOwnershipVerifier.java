package com.legalflow.document.integration;

import com.legalflow.document.security.TenantContext;
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
public class CaseServiceOwnershipVerifier {

    private final RestClient restClient;

    public CaseServiceOwnershipVerifier(RestClient caseServiceRestClient) {
        this.restClient = caseServiceRestClient;
    }

    public void verifyCaseBelongsToCurrentTenant(UUID caseId) {
        JwtAuthenticationToken authentication = TenantContext.requireJwtAuthentication();
        try {
            restClient.get()
                    .uri("/cases/{caseId}", caseId)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + authentication.getToken().getTokenValue())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "case not found in your law firm");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Case service could not verify document ownership.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Case service could not verify document ownership.");
        }
    }
}
