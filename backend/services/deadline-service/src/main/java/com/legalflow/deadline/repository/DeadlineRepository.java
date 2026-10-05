package com.legalflow.deadline.repository;

import com.legalflow.deadline.domain.Deadline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

public interface DeadlineRepository extends JpaRepository<Deadline, UUID> {

    Optional<Deadline> findByIdAndLawFirmId(UUID id, UUID lawFirmId);

    List<Deadline> findAllByLawFirmIdOrderByDueDateAsc(UUID lawFirmId);

    List<Deadline> findAllByLawFirmIdAndCaseIdOrderByDueDateAsc(UUID lawFirmId, UUID caseId);

        List<Deadline> findAllByLawFirmIdAndStatusAndDueDateBetweenOrderByDueDateAsc(
            UUID lawFirmId, com.legalflow.deadline.domain.DeadlineStatus status,
            LocalDateTime start, LocalDateTime end);

        List<Deadline> findAllByStatusAndDueDateLessThanEqual(
            com.legalflow.deadline.domain.DeadlineStatus status, LocalDateTime dueDate);

        List<Deadline> findAllByStatusAndDueDateAfterAndDueDateLessThanEqual(
            com.legalflow.deadline.domain.DeadlineStatus status, LocalDateTime after, LocalDateTime dueDate);
}
