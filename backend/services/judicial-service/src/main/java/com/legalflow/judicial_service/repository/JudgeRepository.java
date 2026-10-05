package com.legalflow.judicial_service.repository;

import com.legalflow.judicial_service.domain.Judge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JudgeRepository extends JpaRepository<Judge, UUID> {
    List<Judge> findByCourtIdOrderByLastNameAscFirstNameAsc(UUID courtId);
}
