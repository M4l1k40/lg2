package com.legalflow.user.dto;

public record UserResponse(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        String role,
        String clientId) {
}
