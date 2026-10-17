package com.legalflow.notification.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Token du compte de service du notification-service (client_credentials), mis en cache jusqu'à son expiration.
 * Sans secret configuré ou si Keycloak est injoignable, renvoie {@code Optional.empty()} : les envois
 * continuent avec les coordonnées présentes dans l'événement.
 */
@Component
public class ServiceAccountTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceAccountTokenProvider.class);
    private static final ParameterizedTypeReference<Map<String, Object>> RAW_MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final Object lock = new Object();
    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;
    private boolean missingSecretReported;

    public ServiceAccountTokenProvider(
            @Qualifier("keycloakTokenRestClient") RestClient restClient,
            @Value("${legalflow.service-account.realm:legalflow}") String realm,
            @Value("${legalflow.service-account.client-id:legalflow-notification-service}") String clientId,
            @Value("${legalflow.service-account.client-secret:}") String clientSecret) {
        this.restClient = restClient;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public Optional<String> accessToken() {
        synchronized (lock) {
            if (cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
                return Optional.of(cachedToken);
            }
            if (clientSecret == null || clientSecret.isBlank()) {
                if (!missingSecretReported) {
                    log.warn("Service account secret is not configured: client contact lookup is disabled.");
                    missingSecretReported = true;
                }
                return Optional.empty();
            }

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            try {
                Map<String, Object> response = restClient.post()
                        .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(RAW_MAP);
                if (response == null || response.get("access_token") == null) {
                    log.warn("Keycloak did not return an access token for the service account.");
                    return Optional.empty();
                }
                Object expiresIn = response.get("expires_in");
                long seconds = expiresIn instanceof Number number ? number.longValue() : 60L;
                cachedToken = response.get("access_token").toString();
                expiresAt = Instant.now().plusSeconds(seconds);
                return Optional.of(cachedToken);
            } catch (RestClientException exception) {
                // Le secret n'est jamais journalisé, ni le corps de la réponse.
                log.warn("Could not obtain a service account token: {}", exception.getClass().getSimpleName());
                return Optional.empty();
            }
        }
    }

    /** À appeler quand un service refuse le token (401/403) : le prochain appel en demandera un nouveau. */
    public void invalidate() {
        synchronized (lock) {
            cachedToken = null;
            expiresAt = Instant.EPOCH;
        }
    }
}
