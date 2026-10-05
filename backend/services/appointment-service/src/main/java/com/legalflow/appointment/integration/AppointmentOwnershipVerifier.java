package com.legalflow.appointment.integration;

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

import java.util.UUID;

@Component
public class AppointmentOwnershipVerifier {

    private final RestClient clientServiceRestClient;
    private final RestClient caseServiceRestClient;

    public AppointmentOwnershipVerifier(
            @org.springframework.beans.factory.annotation.Qualifier("clientServiceRestClient") RestClient clientServiceRestClient,
            @org.springframework.beans.factory.annotation.Qualifier("caseServiceRestClient") RestClient caseServiceRestClient) {
        this.clientServiceRestClient = clientServiceRestClient;
        this.caseServiceRestClient = caseServiceRestClient;
    }

    public void verifyClientBelongsToCurrentTenant(UUID clientId) {
        verifyOwnedResource(clientServiceRestClient, "/clients/{id}", clientId,
                "client not found in your law firm", "Client service");
    }

    public void verifyCaseBelongsToCurrentTenant(UUID caseId) {
        if (caseId != null) {
            verifyOwnedResource(caseServiceRestClient, "/cases/{id}", caseId,
                    "case not found in your law firm", "Case service");
        }
    }

    private void verifyOwnedResource(RestClient restClient, String path, UUID id,
                                     String notFoundMessage, String serviceName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing authenticated JWT.");
        }

        try {
            restClient.get()
                    .uri(path, id)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + jwtAuthentication.getToken().getTokenValue())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, notFoundMessage);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    serviceName + " could not verify appointment ownership.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    serviceName + " could not verify appointment ownership.");
        }
    }
}
