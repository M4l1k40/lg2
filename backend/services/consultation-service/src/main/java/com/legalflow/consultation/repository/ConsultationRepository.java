package com.legalflow.consultation.repository;

import com.legalflow.consultation.domain.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    List<Consultation> findAllByLawFirmIdOrderByCreatedAtDesc(UUID lawFirmId);

    List<Consultation> findAllByLawFirmIdAndClientIdOrderByCreatedAtDesc(UUID lawFirmId, UUID clientId);

    /** Pour un avocat : les demandes à prendre en charge (PENDING) et celles qui lui sont assignées. */
    @Query("select c from Consultation c where c.lawFirmId = :lawFirmId "
            + "and (c.lawyerId = :lawyerId or c.status = com.legalflow.consultation.domain.ConsultationStatus.PENDING) "
            + "order by c.createdAt desc")
    List<Consultation> findVisibleToLawyer(@Param("lawFirmId") UUID lawFirmId, @Param("lawyerId") UUID lawyerId);

    Optional<Consultation> findByIdAndLawFirmId(UUID id, UUID lawFirmId);
}
