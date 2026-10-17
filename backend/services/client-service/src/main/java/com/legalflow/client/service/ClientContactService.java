package com.legalflow.client.service;

import com.legalflow.client.dto.ClientContactResponse;
import com.legalflow.client.repository.ClientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Lecture des coordonnées d'un client pour un compte de service (sans utilisateur connecté, donc sans
 * {@code TenantContext}) : le cabinet est fourni explicitement et la fiche doit lui appartenir.
 * Une fiche d'un autre cabinet est indiscernable d'une fiche inexistante (404).
 */
@Service
public class ClientContactService {

    private final ClientRepository clientRepository;

    public ClientContactService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public ClientContactResponse findContact(UUID clientId, UUID lawFirmId) {
        return clientRepository.findById(clientId)
                .filter(client -> lawFirmId.equals(client.getLawFirmId()))
                .map(client -> new ClientContactResponse(client.getId(), client.getFirstName(),
                        client.getLastName(), client.getEmail(), client.getPhone()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found."));
    }
}
