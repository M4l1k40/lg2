package com.legalflow.user.dto;

/** {@code status} vaut INVITED (compte créé puis invité) ou REINVITED (compte existant, nouvelle invitation). */
public record ClientInvitationResponse(String status, String email) {
}
