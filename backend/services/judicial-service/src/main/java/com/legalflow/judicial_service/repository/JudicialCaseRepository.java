package com.legalflow.judicial_service.repository;

import com.legalflow.judicial_service.domain.JudicialCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JudicialCaseRepository extends JpaRepository<JudicialCase, UUID> {
    List<JudicialCase> findAllByLawFirmIdOrderByOpeningDateDesc(UUID lawFirmId);
}
