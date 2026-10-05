package com.legalflow.deadline.notification;

import com.legalflow.deadline.domain.Deadline;

public interface NotificationPublisher {

    void publishOverdue(Deadline deadline);

    void publishApproaching(Deadline deadline, int thresholdDays);
}
