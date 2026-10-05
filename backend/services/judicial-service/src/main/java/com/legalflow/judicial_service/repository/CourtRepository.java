package com.legalflow.judicial_service.repository;

import com.legalflow.judicial_service.domain.Court;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourtRepository extends JpaRepository<Court, UUID> {
}
