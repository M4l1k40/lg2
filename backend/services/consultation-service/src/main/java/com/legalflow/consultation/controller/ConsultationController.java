package com.legalflow.consultation.controller;

import com.legalflow.consultation.dto.AnswerConsultationRequest;
import com.legalflow.consultation.dto.AssignConsultationRequest;
import com.legalflow.consultation.dto.ConsultationResponse;
import com.legalflow.consultation.dto.CreateConsultationRequest;
import com.legalflow.consultation.dto.UpdateConsultationRequest;
import com.legalflow.consultation.service.ConsultationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/consultations")
public class ConsultationController {

    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @GetMapping
    public List<ConsultationResponse> getConsultations(@RequestParam(required = false) UUID lawFirmId) {
        return consultationService.getConsultations(lawFirmId).stream()
                .map(ConsultationResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationResponse createConsultation(@Valid @RequestBody CreateConsultationRequest request) {
        return ConsultationResponse.from(consultationService.createConsultation(request, null));
    }

    @GetMapping("/{id}")
    public ConsultationResponse getConsultation(@PathVariable UUID id) {
        return ConsultationResponse.from(consultationService.getConsultation(id));
    }

    @PutMapping("/{id}")
    public ConsultationResponse updateConsultation(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateConsultationRequest request) {
        return ConsultationResponse.from(consultationService.updateConsultation(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConsultation(@PathVariable UUID id) {
        consultationService.deleteConsultation(id);
    }

    @PutMapping("/{id}/assign")
    public ConsultationResponse assignConsultation(@PathVariable UUID id,
                                                 @Valid @RequestBody AssignConsultationRequest request) {
        return ConsultationResponse.from(consultationService.assignConsultation(id, request));
    }

    @PutMapping("/{id}/start")
    public ConsultationResponse startConsultation(@PathVariable UUID id) {
        return ConsultationResponse.from(consultationService.startConsultation(id));
    }

    @PutMapping("/{id}/answer")
    public ConsultationResponse answerConsultation(@PathVariable UUID id,
                                                 @Valid @RequestBody AnswerConsultationRequest request) {
        return ConsultationResponse.from(consultationService.answerConsultation(id, request));
    }

    @PutMapping("/{id}/close")
    public ConsultationResponse closeConsultation(@PathVariable UUID id) {
        return ConsultationResponse.from(consultationService.closeConsultation(id));
    }

    @PutMapping("/{id}/cancel")
    public ConsultationResponse cancelConsultation(@PathVariable UUID id) {
        return ConsultationResponse.from(consultationService.cancelConsultation(id));
    }
}
