package com.legalflow.judicial_service.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "judicial_events")
public class JudicialEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String type;

    @Column(length = 2000)
    private String description;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime eventDate;

    @Column
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "judicial_case_id", nullable = false)
    @JsonIgnore
    private JudicialCase judicialCase;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected JudicialEvent() {
    }

    public JudicialEvent(String type, String description, LocalDateTime eventDate, String createdBy) {
        this.type = type;
        this.description = description;
        this.eventDate = eventDate;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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
}
