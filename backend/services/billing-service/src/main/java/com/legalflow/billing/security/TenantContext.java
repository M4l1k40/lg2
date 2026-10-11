package com.legalflow.billing.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
        Object claim = requireJwtAuthentication().getToken().getClaim("lawFirmId");
        if (claim == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT claim 'lawFirmId' is missing.");
        }
        try {
            return UUID.fromString(claim.toString());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "JWT claim 'lawFirmId' is malformed.");
        }
    }

    public static String currentUserSubjectOrNull() {
        String subject = requireJwtAuthentication().getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            return null;
        }
        return subject;
    }

    // ---- Rôles et identité (contrôle d'accès par rôle) ----

    public static boolean hasRole(String role) {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String expected = "ROLE_" + role;
        for (org.springframework.security.core.GrantedAuthority authority : authentication.getAuthorities()) {
            if (expected.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** ADMIN ou SECRETARY. */
    public static boolean isOfficeStaff() {
        return hasRole("ADMIN") || hasRole("SECRETARY");
    }

    public static boolean isLawyer() {
        return hasRole("LAWYER");
    }

    /** ADMIN, SECRETARY ou LAWYER : le personnel du cabinet, par opposition au CLIENT. */
    public static boolean isStaff() {
        return isOfficeStaff() || isLawyer();
    }

    /** Identifiant de la fiche client liée à l'utilisateur CLIENT (claim posé par un administrateur dans Keycloak). */
    public static java.util.UUID requireClientId() {
        return requireUuidClaimValue("clientId");
    }

    /** Identifiant Keycloak (claim sub, au format UUID) de l'utilisateur connecté. */
    public static java.util.UUID requireUserId() {
        return requireUuidClaimValue("sub");
    }

    private static java.util.UUID requireUuidClaimValue(String claimName) {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Missing authenticated JWT.");
        }
        Object claim = jwtAuthenticationToken.getToken().getClaim(claimName);
        if (claim == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "JWT claim '" + claimName + "' is missing.");
        }
        try {
            return java.util.UUID.fromString(claim.toString());
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "JWT claim '" + claimName + "' is malformed.");
        }
    }
}
