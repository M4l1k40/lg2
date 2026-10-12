package com.legalflow.user.dto;

import java.util.List;

public record MeResponse(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        List<String> roles,
        String lawFirmId,
        String clientId) {
}
