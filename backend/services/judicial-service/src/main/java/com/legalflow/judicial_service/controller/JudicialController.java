package com.legalflow.judicial_service.controller;

import com.legalflow.judicial_service.domain.Court;
import com.legalflow.judicial_service.domain.Hearing;
import com.legalflow.judicial_service.domain.HearingRequest;
import com.legalflow.judicial_service.domain.HearingStatus;
import com.legalflow.judicial_service.domain.Judge;
import com.legalflow.judicial_service.domain.JudicialCase;
import com.legalflow.judicial_service.domain.JudicialCaseStatus;
import com.legalflow.judicial_service.domain.JudicialEvent;
import com.legalflow.judicial_service.domain.JudgeRequest;
import com.legalflow.judicial_service.service.JudicialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class JudicialController {

    private final JudicialService judicialService;

    public JudicialController(JudicialService judicialService) {
        this.judicialService = judicialService;
    }

    @GetMapping("/courts")
    public List<Court> getCourts() {
        return judicialService.findAllCourts();
    }

    @GetMapping("/courts/{id}")
    public Court getCourtById(@PathVariable UUID id) {
        return judicialService.findCourtById(id);
    }

    @PostMapping("/courts")
    @ResponseStatus(HttpStatus.CREATED)
    public Court createCourt(@Valid @RequestBody Court court) {
        return judicialService.createCourt(court);
    }

    @GetMapping("/courts/{courtId}/judges")
    public List<Judge> getJudgesByCourt(@PathVariable UUID courtId) {
        return judicialService.findJudgesByCourtId(courtId);
    }

    @PostMapping("/courts/{courtId}/judges")
    @ResponseStatus(HttpStatus.CREATED)
    public Judge createJudge(@PathVariable UUID courtId, @Valid @RequestBody Judge judge) {
        return judicialService.createJudge(courtId, judge);
    }

    @PostMapping("/judges")
    @ResponseStatus(HttpStatus.CREATED)
    public Judge createJudge(@Valid @RequestBody JudgeRequest request) {
        return judicialService.createJudge(request);
    }

    @GetMapping("/judicial-cases")
    public List<JudicialCase> getJudicialCases(@RequestParam(required = false) UUID lawFirmId) {
        return judicialService.findJudicialCasesByLawFirmId(lawFirmId);
    }

    @GetMapping("/judicial-cases/{id}")
    public JudicialCase getJudicialCaseById(@PathVariable UUID id) {
        return judicialService.findJudicialCaseById(id);
    }

    @PostMapping("/judicial-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public JudicialCase createJudicialCase(@Valid @RequestBody JudicialCase judicialCase) {
        return judicialService.createJudicialCase(judicialCase);
    }

    @PatchMapping("/judicial-cases/{id}/status")
    public JudicialCase updateJudicialCaseStatus(@PathVariable UUID id, @RequestParam JudicialCaseStatus status) {
        return judicialService.updateJudicialCaseStatus(id, status);
    }

    @GetMapping("/judicial-cases/{id}/hearings")
    public List<Hearing> getHearings(@PathVariable UUID id) {
        return judicialService.findHearingsByJudicialCaseId(id);
    }

    @GetMapping("/judicial-cases/{id}/events")
    public List<JudicialEvent> getEvents(@PathVariable UUID id) {
        return judicialService.findEventsByJudicialCaseId(id);
    }

    @PostMapping("/judicial-cases/{judicialCaseId}/hearings")
    @ResponseStatus(HttpStatus.CREATED)
    public Hearing scheduleHearing(@PathVariable UUID judicialCaseId, @Valid @RequestBody HearingRequest request) {
        return judicialService.createHearing(judicialCaseId, request);
    }

    @PatchMapping("/hearings/{hearingId}/status")
    public Hearing updateHearingStatus(@PathVariable UUID hearingId, @RequestParam HearingStatus status) {
        return judicialService.updateHearingStatus(hearingId, status);
    }
}
