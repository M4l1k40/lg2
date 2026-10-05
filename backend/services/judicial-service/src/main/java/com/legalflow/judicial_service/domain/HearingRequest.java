package com.legalflow.judicial_service.domain;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class HearingRequest {

    @NotNull
    private LocalDateTime dateTime;

    @NotNull
    private HearingType type;

    private HearingStatus status;
    private String location;
    private String notes;
    private String nextAction;

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public HearingType getType() {
        return type;
    }

    public void setType(HearingType type) {
        this.type = type;
    }

    public HearingStatus getStatus() {
        return status;
    }

    public void setStatus(HearingStatus status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }
}
