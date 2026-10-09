package com.legalflow.case_service.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

public final class TenantContext {

    /** Rôles du personnel du cabinet : ils voient toutes les données de leur cabinet. */
    private static final Set<String> STAFF_ROLES = Set.of("ROLE_ADMIN", "ROLE_LAWYER", "ROLE_SECRETARY");

    private TenantContext() {
    }

    public static UUID requireLawFirmId() {
        return requireUuidClaim("lawFirmId");
    }

    /** Identifiant de la fiche client liée à l'utilisateur CLIENT (claim posé par un administrateur dans Keycloak). */
    public static UUID requireClientId() {
        return requireUuidClaim("clientId");
    }

    /** Vrai si l'utilisateur est ADMIN, LAWYER ou SECRETARY ; faux pour un CLIENT. */
    public static boolean isStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (STAFF_ROLES.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static UUID requireUuidClaim(String claimName) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing authenticated JWT.");
        }

        Jwt jwt = jwtAuthenticationToken.getToken();
        Object claim = jwt.getClaim(claimName);
        if (claim == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT claim '" + claimName + "' is missing.");
        }

        try {
            return UUID.fromString(claim.toString());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT claim '" + claimName + "' is malformed.");
        }
    }
}
