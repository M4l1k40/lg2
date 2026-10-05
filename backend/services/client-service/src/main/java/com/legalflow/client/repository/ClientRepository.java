package com.legalflow.client.repository;

import com.legalflow.client.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findAllByLawFirmId(UUID lawFirmId);

    boolean existsByEmail(String email);
}
