package com.legalflow.user.dto;

/** Informations minimales d'un avocat, destinées aux listes déroulantes (assignation, rendez-vous). */
public record LawyerSummary(String id, String firstName, String lastName, String username) {
}
