package com.legalflow.deadline.dto;

import com.legalflow.deadline.domain.DeadlineStatus;
import jakarta.validation.constraints.NotNull;

public class DeadlineStatusRequest {

    @NotNull
    private DeadlineStatus status;

    public DeadlineStatus getStatus() { return status; }
    public void setStatus(DeadlineStatus status) { this.status = status; }
}
