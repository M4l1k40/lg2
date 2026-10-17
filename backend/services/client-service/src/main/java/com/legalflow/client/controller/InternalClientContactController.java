package com.legalflow.client.controller;

import com.legalflow.client.dto.ClientContactResponse;
import com.legalflow.client.service.ClientContactService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Point d'accès interne, réservé au compte de service du notification-service (rôle SERVICE_CONTACT_LOOKUP).
 * Il n'est pas routé par la gateway (qui ne publie que /api/clients/**) et aucun rôle humain ne le permet.
 */
@RestController
@RequestMapping("/internal/clients")
public class InternalClientContactController {

    private static final Logger log = LoggerFactory.getLogger(InternalClientContactController.class);

    private final ClientContactService clientContactService;

    public InternalClientContactController(ClientContactService clientContactService) {
        this.clientContactService = clientContactService;
    }

    @GetMapping("/{id}/contact")
    @PreAuthorize("hasRole('SERVICE_CONTACT_LOOKUP')")
    public ClientContactResponse contact(@PathVariable UUID id, @RequestParam UUID lawFirmId) {
        // Trace d'audit : qui a accédé à des données personnelles (identifiants seulement, jamais les valeurs).
        log.info("Contact lookup by service account for client {} in law firm {}", id, lawFirmId);
        return clientContactService.findContact(id, lawFirmId);
    }
}
