package com.legalflow.deadline.controller;

import com.legalflow.deadline.domain.Deadline;
import com.legalflow.deadline.domain.DeadlineStatus;
import com.legalflow.deadline.dto.DeadlineRequest;
import com.legalflow.deadline.dto.DeadlineStatusRequest;
import com.legalflow.deadline.service.DeadlineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/deadlines")
public class DeadlineController {

    private final DeadlineService deadlineService;

    public DeadlineController(DeadlineService deadlineService) {
        this.deadlineService = deadlineService;
    }

    @GetMapping
    public List<Deadline> getDeadlines(@RequestParam(required = false) UUID lawFirmId) {
        return deadlineService.getDeadlines(lawFirmId);
    }

    @GetMapping("/approaching")
    public List<Deadline> getApproachingDeadlines(
            @RequestParam(required = false) UUID lawFirmId,
            @RequestParam(defaultValue = "7") int days) {
        return deadlineService.getApproachingDeadlines(lawFirmId, days);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Deadline createDeadline(@Valid @RequestBody DeadlineRequest request) {
        return deadlineService.createDeadline(request);
    }

    @GetMapping("/{id}")
    public Deadline getDeadline(@PathVariable UUID id) {
        return deadlineService.getDeadline(id);
    }

    @GetMapping("/case/{caseId}")
    public List<Deadline> getDeadlinesByCase(@PathVariable UUID caseId) {
        return deadlineService.getDeadlinesByCase(caseId);
    }

    @PutMapping("/{id}")
    public Deadline updateDeadline(@PathVariable UUID id, @Valid @RequestBody DeadlineRequest request) {
        return deadlineService.updateDeadline(id, request);
    }

    @PatchMapping("/{id}/status")
    public Deadline updateStatus(@PathVariable UUID id,
                                 @RequestParam(required = false) String status,
                                 @RequestBody(required = false) DeadlineStatusRequest request) {
        String requestedStatus = status != null ? status : request == null || request.getStatus() == null
                ? null : request.getStatus().name();
        if (requestedStatus == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status is required.");
        }
        return deadlineService.updateStatus(id, requestedStatus);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeadline(@PathVariable UUID id) {
        deadlineService.deleteDeadline(id);
    }
}
