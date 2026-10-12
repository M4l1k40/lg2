package com.legalflow.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Le mot de passe temporaire n'est renvoyé qu'une fois, et uniquement s'il a été généré par le serveur. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreatedUserResponse(UserResponse user, String temporaryPassword) {
}
