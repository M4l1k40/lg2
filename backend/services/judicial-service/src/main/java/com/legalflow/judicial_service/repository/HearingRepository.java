package com.legalflow.judicial_service.repository;

import com.legalflow.judicial_service.domain.Hearing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HearingRepository extends JpaRepository<Hearing, UUID> {
    List<Hearing> findAllByJudicialCase_IdOrderByDateTimeAsc(UUID judicialCaseId);
}
