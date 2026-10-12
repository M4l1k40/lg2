package com.legalflow.client.service;

import com.legalflow.client.domain.Client;
import com.legalflow.client.repository.ClientRepository;
import com.legalflow.client.security.TenantContext;
import com.legalflow.client.exception.ClientConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientService.class);

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<Client> findAllByLawFirmId(UUID requestedLawFirmId) {
        UUID tenantId = TenantContext.requireLawFirmId();
        if (requestedLawFirmId != null && !tenantId.equals(requestedLawFirmId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "lawFirmId query parameter does not match your tenant.");
        }
        if (TenantContext.isOfficeStaff()) {
            return clientRepository.findAllByLawFirmId(tenantId);
        }
        if (TenantContext.isLawyer()) {
            // Un avocat ne voit que les clients dont il est en charge.
            return clientRepository.findAllByLawFirmIdAndLawyerId(tenantId, TenantContext.requireUserId());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clients cannot list clients.");
    }

    @Transactional(readOnly = true)
    public Client findCurrentClient() {
        return findById(TenantContext.requireClientId());
    }

    @Transactional(readOnly = true)
    public Client findById(UUID id) {
        UUID tenantId = TenantContext.requireLawFirmId();
        // Un utilisateur CLIENT ne peut lire que sa propre fiche (réponse 404 pour ne rien révéler des autres)
        if (!TenantContext.isStaff() && !id.equals(TenantContext.requireClientId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + id);
        }
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + id));
        if (!tenantId.equals(client.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + id);
        }
        if (!TenantContext.isOfficeStaff() && TenantContext.isLawyer()
                && !client.getLawyerIds().contains(TenantContext.requireUserId())) {
            // 404 plutôt que 403 : on ne révèle pas l'existence des clients des autres avocats.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + id);
        }
        return client;
    }

    @Transactional
    public Client save(Client client) {
        UUID tenantId = TenantContext.requireLawFirmId();
        if (client.getLawFirmId() != null && !tenantId.equals(client.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "lawFirmId in request body does not match your tenant.");
        }
        client.setLawFirmId(tenantId);
        if (TenantContext.isOfficeStaff()) {
            // L'administrateur ou la secrétaire désigne les avocats en charge (éventuellement aucun pour l'instant).
            client.setLawyerIds(client.getLawyerIds());
        } else {
            // Un avocat qui crée un client en devient le responsable : toute valeur fournie dans la requête est ignorée.
            client.setLawyerIds(Set.of(TenantContext.requireUserId()));
        }
        if (clientRepository.existsByEmail(client.getEmail())) {
            throw duplicateEmailException(null);
        }
        try {
            return clientRepository.save(client);
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("Client insert violated a database constraint; treating it as a duplicate email.", exception);
            throw duplicateEmailException(exception);
        }
    }

    private ClientConflictException duplicateEmailException(Throwable cause) {
        return new ClientConflictException("EMAIL_ALREADY_EXISTS",
                "Un client avec cet email existe déjà.", cause);
    }

    @Transactional
    public Client updateLawyers(UUID id, Set<UUID> lawyerIds) {
        if (!TenantContext.isOfficeStaff()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the office staff can assign lawyers to a client.");
        }
        Client client = findById(id);
        client.setLawyerIds(lawyerIds);
        return clientRepository.save(client);
    }
}
