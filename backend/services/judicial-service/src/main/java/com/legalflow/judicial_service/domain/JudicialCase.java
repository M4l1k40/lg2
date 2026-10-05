package com.legalflow.judicial_service.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "judicial_cases")
public class JudicialCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID lawFirmId;

    @NotNull
    @Column(nullable = false)
    private UUID caseId;

    @NotNull
    @Column(nullable = false)
    private UUID courtId;

    @NotNull
    @Column(nullable = false)
    private UUID judgeId;

    @NotBlank
    @Column(nullable = false)
    private String courtCaseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcedureType procedureType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JudicialCaseStatus status;

    @NotNull
    @Column(nullable = false)
    private LocalDate openingDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "judicialCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("judicialCase")
    private List<Hearing> hearings = new ArrayList<>();

    @OneToMany(mappedBy = "judicialCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("judicialCase")
    private List<JudicialEvent> events = new ArrayList<>();

    protected JudicialCase() {
    }

    public JudicialCase(UUID lawFirmId, UUID caseId, UUID courtId, UUID judgeId, String courtCaseNumber,
                       ProcedureType procedureType, JudicialCaseStatus status, LocalDate openingDate) {
        this.lawFirmId = lawFirmId;
        this.caseId = caseId;
        this.courtId = courtId;
        this.judgeId = judgeId;
        this.courtCaseNumber = courtCaseNumber;
        this.procedureType = procedureType;
        this.status = status;
        this.openingDate = openingDate;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLawFirmId() {
        return lawFirmId;
    }

    public void setLawFirmId(UUID lawFirmId) {
        this.lawFirmId = lawFirmId;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }

    public UUID getCourtId() {
        return courtId;
    }

    public void setCourtId(UUID courtId) {
        this.courtId = courtId;
    }

    public UUID getJudgeId() {
        return judgeId;
    }

    public void setJudgeId(UUID judgeId) {
        this.judgeId = judgeId;
    }

    public String getCourtCaseNumber() {
        return courtCaseNumber;
    }

    public void setCourtCaseNumber(String courtCaseNumber) {
        this.courtCaseNumber = courtCaseNumber;
    }

    public ProcedureType getProcedureType() {
        return procedureType;
    }

    public void setProcedureType(ProcedureType procedureType) {
        this.procedureType = procedureType;
    }

    public JudicialCaseStatus getStatus() {
        return status;
    }

    public void setStatus(JudicialCaseStatus status) {
        this.status = status;
    }

    public LocalDate getOpeningDate() {
        return openingDate;
    }

    public void setOpeningDate(LocalDate openingDate) {
        this.openingDate = openingDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<Hearing> getHearings() {
        return hearings;
    }

    public List<JudicialEvent> getEvents() {
        return events;
    }

    public void addHearing(Hearing hearing) {
        hearings.add(hearing);
        hearing.setJudicialCase(this);
    }

    public void addEvent(JudicialEvent event) {
        events.add(event);
        event.setJudicialCase(this);
    }
}
