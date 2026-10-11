package com.legalflow.consultation.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public final class TenantContext {

    private TenantContext() {
    }

    public static JwtAuthenticationToken requireJwtAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing authenticated JWT.");
        }
        return jwtAuthentication;
    }

    public static UUID requireLawFirmId() {
        return requireUuidClaim("lawFirmId");
    }

    /** Identifiant de la fiche client liée à l'utilisateur CLIENT (claim posé par un administrateur dans Keycloak). */
    public static UUID requireClientId() {
        return requireUuidClaim("clientId");
    }

    public static String currentUserSubjectOrNull() {
        String subject = requireJwtAuthentication().getToken().getSubject();
        return (subject == null || subject.isBlank()) ? null : subject;
    }

    /** Identifiant Keycloak (claim sub, au format UUID) de l'utilisateur connecté. */
    public static UUID requireUserId() {
        String subject = currentUserSubjectOrNull();
        if (subject == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT subject is missing.");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT subject is malformed.");
        }
    }

    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String expected = "ROLE_" + role;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (expected.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** ADMIN ou SECRETARY : voient et gèrent toutes les consultations du cabinet. */
    public static boolean isOfficeStaff() {
        return hasRole("ADMIN") || hasRole("SECRETARY");
    }

    public static boolean isLawyer() {
        return hasRole("LAWYER");
    }

    private static UUID requireUuidClaim(String claimName) {
        Object claim = requireJwtAuthentication().getToken().getClaim(claimName);
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
