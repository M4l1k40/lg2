package com.legalflow.deadline.notification;

import com.legalflow.deadline.domain.Deadline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationPublisher implements NotificationPublisher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingNotificationPublisher.class);

    @Override
    public void publishOverdue(Deadline deadline) {
        logger.info("Deadline notification: overdue deadlineId={} lawFirmId={} dueDate={}",
                deadline.getId(), deadline.getLawFirmId(), deadline.getDueDate());
    }

    @Override
    public void publishApproaching(Deadline deadline, int thresholdDays) {
        logger.info("Deadline notification: approaching deadlineId={} lawFirmId={} thresholdDays={} dueDate={}",
                deadline.getId(), deadline.getLawFirmId(), thresholdDays, deadline.getDueDate());
    }
}
