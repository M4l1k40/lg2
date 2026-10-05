package com.legalflow.document.repository;

import com.legalflow.document.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndLawFirmId(UUID id, UUID lawFirmId);

    List<Document> findAllByLawFirmIdOrderByCreatedAtDesc(UUID lawFirmId);

    List<Document> findAllByLawFirmIdAndCaseIdOrderByCreatedAtDesc(UUID lawFirmId, UUID caseId);
}
