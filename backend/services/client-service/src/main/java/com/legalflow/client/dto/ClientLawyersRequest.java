package com.legalflow.client.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/** Avocats à mettre en charge d'un client (remplace la liste existante). */
public record ClientLawyersRequest(@NotNull Set<UUID> lawyerIds) {
}
