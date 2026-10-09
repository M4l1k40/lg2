package com.legalflow.client.controller;

import com.legalflow.client.domain.Client;
import com.legalflow.client.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    /** Liste des clients du cabinet : réservée au personnel. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','LAWYER','SECRETARY')")
    public List<Client> findAll(@RequestParam(required = false) UUID lawFirmId) {
        return clientService.findAllByLawFirmId(lawFirmId);
    }

    /** Fiche de l'utilisateur CLIENT connecté. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    public Client me() {
        return clientService.findCurrentClient();
    }

    /** Personnel : n'importe quelle fiche du cabinet. Client : uniquement sa propre fiche. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LAWYER','SECRETARY','CLIENT')")
    public Client findById(@PathVariable UUID id) {
        return clientService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','LAWYER','SECRETARY')")
    public Client create(@Valid @RequestBody Client client) {
        return clientService.save(client);
    }
}
