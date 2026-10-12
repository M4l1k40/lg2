package com.legalflow.user.service;

import com.legalflow.user.dto.CreateUserRequest;
import com.legalflow.user.dto.CreatedUserResponse;
import com.legalflow.user.dto.LawyerSummary;
import com.legalflow.user.dto.MeResponse;
import com.legalflow.user.dto.PasswordResetResponse;
import com.legalflow.user.dto.UserResponse;
import com.legalflow.user.integration.ClientServiceOwnershipVerifier;
import com.legalflow.user.keycloak.KeycloakAdminClient;
import com.legalflow.user.keycloak.KeycloakUser;
import com.legalflow.user.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Annuaire et provisioning des utilisateurs d'un cabinet. Keycloak reste la source de vérité ;
 * chaque opération est limitée au cabinet (lawFirmId) de l'administrateur connecté.
 */
@Service
public class UserDirectoryService {

    private static final List<String> ROLE_PRIORITY = List.of("ADMIN", "LAWYER", "SECRETARY", "CLIENT");
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int GENERATED_PASSWORD_LENGTH = 14;

    private final KeycloakAdminClient keycloak;
    private final ClientServiceOwnershipVerifier clientVerifier;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserDirectoryService(KeycloakAdminClient keycloak, ClientServiceOwnershipVerifier clientVerifier) {
        this.keycloak = keycloak;
        this.clientVerifier = clientVerifier;
    }

    /** Profil de l'utilisateur connecté, construit à partir de son token (aucun appel à Keycloak). */
    public MeResponse currentUser() {
        Jwt jwt = TenantContext.requireJwtAuthentication().getToken();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        List<String> roles = List.of();
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> rawRoles) {
            roles = rawRoles.stream().map(Object::toString).filter(ROLE_PRIORITY::contains).toList();
        }
        return new MeResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                roles,
                jwt.getClaimAsString("lawFirmId"),
                jwt.getClaimAsString("clientId"));
    }

    public List<UserResponse> listUsers() {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        List<KeycloakUser> users = keycloak.findUsersByLawFirm(lawFirmId);
        Map<String, String> roleByUserId = resolveRoles();
        return users.stream()
                .map(user -> toResponse(user, roleByUserId.get(user.id())))
                .toList();
    }

    /** Avocats actifs du cabinet : alimente les listes d'assignation (consultations, rendez-vous). */
    public List<LawyerSummary> listLawyers() {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        Set<String> lawyerIds = keycloak.findUserIdsByRole("LAWYER");
        return keycloak.findUsersByLawFirm(lawFirmId).stream()
                .filter(user -> lawyerIds.contains(user.id()) && !Boolean.FALSE.equals(user.enabled()))
                .map(user -> new LawyerSummary(user.id(), user.firstName(), user.lastName(), user.username()))
                .sorted(Comparator.comparing(LawyerSummary::lastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    public CreatedUserResponse createUser(CreateUserRequest request) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        String role = request.role().toUpperCase(Locale.ROOT);

        Map<String, List<String>> attributes = new LinkedHashMap<>();
        attributes.put("lawFirmId", List.of(lawFirmId.toString()));

        if ("CLIENT".equals(role)) {
            if (request.clientId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId is required for the CLIENT role.");
            }
            // La fiche doit exister dans le cabinet de l'administrateur (sinon on pourrait lier un compte à un autre cabinet).
            clientVerifier.verifyClientBelongsToCurrentTenant(request.clientId());
            attributes.put("clientId", List.of(request.clientId().toString()));
        } else if (request.clientId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId is only allowed for the CLIENT role.");
        }

        boolean generated = request.temporaryPassword() == null || request.temporaryPassword().isBlank();
        String password = generated ? generatePassword() : request.temporaryPassword();
        String username = request.username().trim().toLowerCase(Locale.ROOT);

        String userId = keycloak.createUser(username, request.email().trim(), request.firstName().trim(),
                request.lastName().trim(), attributes, password);
        try {
            keycloak.addRealmRole(userId, role);
        } catch (RuntimeException exception) {
            // Pas de compte à moitié créé : sans rôle, l'utilisateur serait inutilisable.
            try {
                keycloak.deleteUser(userId);
            } catch (RuntimeException ignored) {
                // meilleur effort
            }
            throw exception;
        }

        UserResponse user = new UserResponse(userId, username, request.email().trim(), request.firstName().trim(),
                request.lastName().trim(), true, role, request.clientId() == null ? null : request.clientId().toString());
        return new CreatedUserResponse(user, generated ? password : null);
    }

    public UserResponse setEnabled(String userId, boolean enabled) {
        KeycloakUser user = requireUserOfCurrentFirm(userId);
        if (!enabled && TenantContext.requireUserId().toString().equalsIgnoreCase(user.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot disable your own account.");
        }
        keycloak.setEnabled(user.id(), enabled);
        KeycloakUser updated = keycloak.findUser(user.id()).orElse(user);
        return toResponse(updated, resolveRoles().get(user.id()));
    }

    public PasswordResetResponse resetPassword(String userId) {
        KeycloakUser user = requireUserOfCurrentFirm(userId);
        String password = generatePassword();
        keycloak.resetPassword(user.id(), password, true);
        return new PasswordResetResponse(password);
    }

    /** 404 pour un utilisateur inexistant OU appartenant à un autre cabinet : on ne révèle rien. */
    private KeycloakUser requireUserOfCurrentFirm(String userId) {
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        try {
            UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw notFound;
        }
        UUID lawFirmId = TenantContext.requireLawFirmId();
        KeycloakUser user = keycloak.findUser(userId).orElseThrow(() -> notFound);
        if (!lawFirmId.toString().equalsIgnoreCase(user.attribute("lawFirmId"))) {
            throw notFound;
        }
        return user;
    }

    /** Rôle principal de chaque utilisateur (ADMIN > LAWYER > SECRETARY > CLIENT). */
    private Map<String, String> resolveRoles() {
        Map<String, String> roleByUserId = new LinkedHashMap<>();
        for (String role : ROLE_PRIORITY) {
            for (String userId : keycloak.findUserIdsByRole(role)) {
                roleByUserId.putIfAbsent(userId, role);
            }
        }
        return roleByUserId;
    }

    private UserResponse toResponse(KeycloakUser user, String role) {
        return new UserResponse(user.id(), user.username(), user.email(), user.firstName(), user.lastName(),
                !Boolean.FALSE.equals(user.enabled()), role, user.attribute("clientId"));
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET.charAt(secureRandom.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }
}
