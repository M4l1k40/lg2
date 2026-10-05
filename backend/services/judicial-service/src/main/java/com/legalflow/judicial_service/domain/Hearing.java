package com.legalflow.judicial_service.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "hearings")
public class Hearing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "judicial_case_id", nullable = false)
    @JsonIgnore
    private JudicialCase judicialCase;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HearingType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HearingStatus status;

    @Column(length = 500)
    private String location;

    @Column(length = 2000)
    private String notes;

    @Column(length = 2000)
    private String nextAction;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Hearing() {
    }

    public Hearing(JudicialCase judicialCase, LocalDateTime dateTime, HearingType type, HearingStatus status,
                  String location, String notes, String nextAction) {
        this.judicialCase = judicialCase;
        this.dateTime = dateTime;
        this.type = type;
        this.status = status;
        this.location = location;
        this.notes = notes;
        this.nextAction = nextAction;
    }

    public UUID getId() {
        return id;
    }

    public JudicialCase getJudicialCase() {
        return judicialCase;
    }

    @JsonProperty("judicialCaseId")
    public UUID getJudicialCaseId() {
        return judicialCase == null ? null : judicialCase.getId();
    }

    public void setJudicialCase(JudicialCase judicialCase) {
        this.judicialCase = judicialCase;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
