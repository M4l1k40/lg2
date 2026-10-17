package com.legalflow.client.dto;

import java.util.UUID;

/** Coordonnées d'un client pour les envois de notifications : volontairement sans adresse ni avocats. */
public record ClientContactResponse(UUID id, String firstName, String lastName, String email, String phone) {
}
