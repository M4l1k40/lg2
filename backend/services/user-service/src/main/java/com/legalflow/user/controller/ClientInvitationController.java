package com.legalflow.user.controller;

import com.legalflow.user.dto.ClientInvitationResponse;
import com.legalflow.user.service.ClientInvitationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/users/clients")
public class ClientInvitationController {

    private final ClientInvitationService clientInvitationService;

    public ClientInvitationController(ClientInvitationService clientInvitationService) {
        this.clientInvitationService = clientInvitationService;
    }

    /** Invite (ou réinvite) le client à créer son accès. Réservé au personnel du cabinet. */
    @PostMapping("/{clientId}/invitation")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER')")
    public ClientInvitationResponse invite(@PathVariable UUID clientId) {
        return clientInvitationService.invite(clientId);
    }
}
