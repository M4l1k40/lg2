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
        return clientRepository.findAllByLawFirmId(tenantId);
    }

    @Transactional(readOnly = true)
    public Client findById(UUID id) {
        UUID tenantId = TenantContext.requireLawFirmId();
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + id));
        if (!tenantId.equals(client.getLawFirmId())) {
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
}
