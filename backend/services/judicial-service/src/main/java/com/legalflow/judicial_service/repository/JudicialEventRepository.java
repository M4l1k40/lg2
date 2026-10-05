package com.legalflow.judicial_service.repository;

import com.legalflow.judicial_service.domain.JudicialEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JudicialEventRepository extends JpaRepository<JudicialEvent, UUID> {
    List<JudicialEvent> findAllByJudicialCase_IdOrderByEventDateAsc(UUID judicialCaseId);
}
