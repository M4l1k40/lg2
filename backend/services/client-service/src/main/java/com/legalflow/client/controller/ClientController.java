package com.legalflow.client.controller;

import com.legalflow.client.domain.Client;
import com.legalflow.client.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @GetMapping
    public List<Client> findAll(@RequestParam(required = false) UUID lawFirmId) {
        return clientService.findAllByLawFirmId(lawFirmId);
    }

    @GetMapping("/{id}")
    public Client findById(@PathVariable UUID id) {
        return clientService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Client create(@Valid @RequestBody Client client) {
        return clientService.save(client);
    }
}
