package com.legalflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Création d'un compte dans le cabinet de l'administrateur connecté.
 * Le cabinet (lawFirmId) n'est jamais fourni par la requête : il vient du token.
 */
public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "username may only contain letters, digits, '.', '_' and '-'")
        String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Pattern(regexp = "ADMIN|LAWYER|SECRETARY|CLIENT", message = "role must be ADMIN, LAWYER, SECRETARY or CLIENT")
        String role,
        /** Obligatoire pour le rôle CLIENT : fiche client à laquelle le compte est lié. */
        UUID clientId,
        /** Facultatif : généré automatiquement s'il est absent. */
        @Size(min = 10, max = 128) String temporaryPassword) {
}
