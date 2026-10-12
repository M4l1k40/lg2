package com.legalflow.user.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** Représentation (partielle) d'un utilisateur Keycloak. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Map<String, List<String>> attributes) {

    /** Première valeur d'un attribut (ex. lawFirmId, clientId), ou null. */
    public String attribute(String name) {
        if (attributes == null) {
            return null;
        }
        List<String> values = attributes.get(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
