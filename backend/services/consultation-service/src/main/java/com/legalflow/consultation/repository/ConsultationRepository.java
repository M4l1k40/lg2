package com.legalflow.consultation.repository;

import com.legalflow.consultation.domain.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

    List<Consultation> findAllByLawFirmIdOrderByCreatedAtDesc(UUID lawFirmId);

    Optional<Consultation> findByIdAndLawFirmId(UUID id, UUID lawFirmId);
}
