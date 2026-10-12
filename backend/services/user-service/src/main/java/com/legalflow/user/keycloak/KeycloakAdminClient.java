package com.legalflow.user.keycloak;

import com.legalflow.user.config.KeycloakProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client minimal de l'API d'administration de Keycloak, utilisant le compte de service du user-service
 * (client_credentials). Le secret reste dans ce service : il n'est jamais exposé au navigateur.
 */
@Component
public class KeycloakAdminClient {

    private static final ParameterizedTypeReference<List<KeycloakUser>> USER_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Map<String, Object>> RAW_MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final KeycloakProperties properties;
    private final Object tokenLock = new Object();
    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakAdminClient(@Qualifier("keycloakRestClient") RestClient keycloakRestClient,
                               KeycloakProperties properties) {
        this.restClient = keycloakRestClient;
        this.properties = properties;
    }

    /** Utilisateurs dont l'attribut lawFirmId correspond au cabinet donné. */
    public List<KeycloakUser> findUsersByLawFirm(UUID lawFirmId) {
        List<KeycloakUser> users = call(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/realms/{realm}/users")
                        .queryParam("q", "lawFirmId:" + lawFirmId)
                        .queryParam("max", 1000)
                        .queryParam("briefRepresentation", false)
                        .build(properties.realm()))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(USER_LIST));
        return users == null ? List.of() : users;
    }

    /** Identifiants des utilisateurs qui ont un rôle realm donné (tous cabinets confondus). */
    public Set<String> findUserIdsByRole(String role) {
        List<KeycloakUser> users = call(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/admin/realms/{realm}/roles/{role}/users")
                        .queryParam("max", 1000)
                        .build(properties.realm(), role))
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(USER_LIST));
        Set<String> ids = new LinkedHashSet<>();
        if (users != null) {
            users.forEach(user -> ids.add(user.id()));
        }
        return ids;
    }

    public Optional<KeycloakUser> findUser(String userId) {
        KeycloakUser user = call(() -> {
            try {
                return restClient.get()
                        .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .retrieve()
                        .body(KeycloakUser.class);
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                    return null;
                }
                throw exception;
            }
        });
        return Optional.ofNullable(user);
    }

    /** Crée un utilisateur avec un mot de passe temporaire (changement obligatoire à la première connexion). */
    public String createUser(String username, String email, String firstName, String lastName,
                             Map<String, List<String>> attributes, String temporaryPassword) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("enabled", true);
        body.put("emailVerified", true);
        body.put("attributes", attributes);
        body.put("credentials", List.of(Map.of("type", "password", "value", temporaryPassword, "temporary", true)));

        ResponseEntity<Void> response = call(() -> restClient.post()
                .uri("/admin/realms/{realm}/users", properties.realm())
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());

        URI location = response == null ? null : response.getHeaders().getLocation();
        if (location == null || location.getPath() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak did not return the created user id.");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public void addRealmRole(String userId, String roleName) {
        Map<String, Object> role = call(() -> restClient.get()
                .uri("/admin/realms/{realm}/roles/{role}", properties.realm(), roleName)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(RAW_MAP));
        call(() -> restClient.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity());
    }

    /** Active ou désactive un compte : on relit la représentation complète pour ne rien écraser. */
    public void setEnabled(String userId, boolean enabled) {
        Map<String, Object> user = call(() -> restClient.get()
                .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(RAW_MAP));
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        user.put("enabled", enabled);
        call(() -> restClient.put()
                .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity());
    }

    public void resetPassword(String userId, String password, boolean temporary) {
        call(() -> restClient.put()
                .uri("/admin/realms/{realm}/users/{id}/reset-password", properties.realm(), userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", password, "temporary", temporary))
                .retrieve()
                .toBodilessEntity());
    }

    public void deleteUser(String userId) {
        call(() -> restClient.delete()
                .uri("/admin/realms/{realm}/users/{id}", properties.realm(), userId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .toBodilessEntity());
    }

    private String bearer() {
        return "Bearer " + accessToken();
    }

    private String accessToken() {
        synchronized (tokenLock) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
                return cachedToken;
            }
            if (properties.clientSecret() == null || properties.clientSecret().isBlank()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The Keycloak service account secret is not configured (KEYCLOAK_ADMIN_CLIENT_SECRET).");
            }

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.clientId());
            form.add("client_secret", properties.clientSecret());

            Map<String, Object> response;
            try {
                response = restClient.post()
                        .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(RAW_MAP);
            } catch (RestClientException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Could not obtain a Keycloak service account token.");
            }
            if (response == null || response.get("access_token") == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Keycloak did not return an access token.");
            }

            Object expiresIn = response.get("expires_in");
            long seconds = expiresIn instanceof Number number ? number.longValue() : 60L;
            cachedToken = response.get("access_token").toString();
            tokenExpiresAt = Instant.now().plusSeconds(seconds);
            return cachedToken;
        }
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A user with this username or email already exists.");
            }
            if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Keycloak refused the service account request: check its realm-management roles.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak returned an unexpected error (" + status + ").");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Keycloak is unreachable.");
        }
    }
}
