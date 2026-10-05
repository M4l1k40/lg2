package com.legalflow.deadline.service;

import com.legalflow.deadline.domain.Deadline;
import com.legalflow.deadline.domain.DeadlineNotificationEvent;
import com.legalflow.deadline.domain.DeadlineStatus;
import com.legalflow.deadline.notification.NotificationPublisher;
import com.legalflow.deadline.repository.DeadlineNotificationEventRepository;
import com.legalflow.deadline.repository.DeadlineRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Component
public class DeadlineScheduler {

    private final DeadlineRepository deadlineRepository;
    private final DeadlineNotificationEventRepository eventRepository;
    private final NotificationPublisher notificationPublisher;
    private final List<Integer> approachingThresholdsDays;

    public DeadlineScheduler(DeadlineRepository deadlineRepository,
                             DeadlineNotificationEventRepository eventRepository,
                             NotificationPublisher notificationPublisher,
                             @Value("${legalflow.deadlines.approaching-thresholds-days:7,3,1}") String thresholds) {
        this.deadlineRepository = deadlineRepository;
        this.eventRepository = eventRepository;
        this.notificationPublisher = notificationPublisher;
        this.approachingThresholdsDays = Arrays.stream(thresholds.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .filter(days -> days > 0)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    @Scheduled(fixedDelayString = "${legalflow.deadlines.processing-interval-ms:60000}")
    @Transactional
    public void processDeadlines() {
        LocalDateTime now = LocalDateTime.now();

        for (Deadline deadline : deadlineRepository.findAllByStatusAndDueDateLessThanEqual(
                DeadlineStatus.PENDING, now)) {
            deadline.setStatus(DeadlineStatus.OVERDUE);
            deadlineRepository.save(deadline);
            publishOnce(deadline, "OVERDUE", () -> notificationPublisher.publishOverdue(deadline));
        }

        for (int thresholdDays : approachingThresholdsDays) {
            LocalDateTime thresholdDate = now.plusDays(thresholdDays);
            List<Deadline> approaching = deadlineRepository
                    .findAllByStatusAndDueDateAfterAndDueDateLessThanEqual(
                            DeadlineStatus.PENDING, now, thresholdDate);
            for (Deadline deadline : approaching) {
                String eventKey = "APPROACHING_" + thresholdDays;
                publishOnce(deadline, eventKey,
                        () -> notificationPublisher.publishApproaching(deadline, thresholdDays));
            }
        }
    }

    private void publishOnce(Deadline deadline, String eventKey, Runnable publish) {
        if (!eventRepository.existsByDeadlineIdAndEventKey(deadline.getId(), eventKey)) {
            eventRepository.saveAndFlush(new DeadlineNotificationEvent(deadline.getId(), eventKey));
            publish.run();
        }
    }
}
