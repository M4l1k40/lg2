package com.legalflow.case_service.repository;

import com.legalflow.case_service.domain.CaseEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseEventRepository extends JpaRepository<CaseEvent, UUID> {

    List<CaseEvent> findAllByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
