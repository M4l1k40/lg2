package com.legalflow.client.repository;

import com.legalflow.client.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findAllByLawFirmId(UUID lawFirmId);

    /** Clients dont l'avocat donné est en charge. */
    @Query("select c from Client c where c.lawFirmId = :lawFirmId and :lawyerId member of c.lawyerIds")
    List<Client> findAllByLawFirmIdAndLawyerId(@Param("lawFirmId") UUID lawFirmId, @Param("lawyerId") UUID lawyerId);

    boolean existsByEmail(String email);
}
