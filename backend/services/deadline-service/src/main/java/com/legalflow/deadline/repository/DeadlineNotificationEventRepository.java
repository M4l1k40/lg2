package com.legalflow.deadline.repository;

import com.legalflow.deadline.domain.DeadlineNotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeadlineNotificationEventRepository extends JpaRepository<DeadlineNotificationEvent, UUID> {

    boolean existsByDeadlineIdAndEventKey(UUID deadlineId, String eventKey);
}
