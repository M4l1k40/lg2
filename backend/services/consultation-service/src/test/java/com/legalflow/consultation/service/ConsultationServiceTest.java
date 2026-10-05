package com.legalflow.consultation.service;

import com.legalflow.consultation.domain.Consultation;
import com.legalflow.consultation.domain.ConsultationStatus;
import com.legalflow.consultation.dto.AnswerConsultationRequest;
import com.legalflow.consultation.dto.AssignConsultationRequest;
import com.legalflow.consultation.dto.CreateConsultationRequest;
import com.legalflow.consultation.integration.ClientServiceOwnershipVerifier;
import com.legalflow.consultation.repository.ConsultationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultationServiceTest {

    @Test
    void createConsultationUsesAuthenticatedTenantAndVerifiesClient() {
        ConsultationRepository repository = mock(ConsultationRepository.class);
        ClientServiceOwnershipVerifier verifier = mock(ClientServiceOwnershipVerifier.class);
        ConsultationService service = new ConsultationService(repository, verifier);

        UUID firmA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID clientId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        setCurrentTenant(firmA);

        CreateConsultationRequest request = new CreateConsultationRequest();
        request.setClientId(clientId);
        request.setSubject("Question");
        request.setDescription("Need legal advice");

        Consultation saved = new Consultation(firmA, clientId, null, "Question", "Need legal advice", ConsultationStatus.PENDING, null);
        when(repository.save(any(Consultation.class))).thenReturn(saved);

        Consultation result = service.createConsultation(request, null);

        assertEquals(firmA, result.getLawFirmId());
        assertEquals(ConsultationStatus.PENDING, result.getStatus());
        verify(verifier).verifyClientBelongsToCurrentTenant(clientId);
    }

    @Test
    void invalidTransitionThrowsBusinessException() {
        ConsultationRepository repository = mock(ConsultationRepository.class);
        ClientServiceOwnershipVerifier verifier = mock(ClientServiceOwnershipVerifier.class);
        ConsultationService service = new ConsultationService(repository, verifier);

        UUID firmA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        setCurrentTenant(firmA);

        UUID consultationId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Consultation consultation = new Consultation(
                firmA,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                null,
                "Question",
                "Need advice",
                ConsultationStatus.ANSWERED,
                null
        );
        consultation.setAnsweredAt(LocalDateTime.now());
        when(repository.findByIdAndLawFirmId(consultationId, firmA)).thenReturn(Optional.of(consultation));

        AssignConsultationRequest assignRequest = new AssignConsultationRequest();
        assignRequest.setLawyerId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.assignConsultation(consultationId, assignRequest));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void getConsultationRejectsMissingTenantMatch() {
        ConsultationRepository repository = mock(ConsultationRepository.class);
        ClientServiceOwnershipVerifier verifier = mock(ClientServiceOwnershipVerifier.class);
        ConsultationService service = new ConsultationService(repository, verifier);

        UUID consultationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID firmA = UUID.fromString("11111111-1111-1111-1111-111111111111");

        setCurrentTenant(firmA);
        when(repository.findByIdAndLawFirmId(consultationId, firmA)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.getConsultation(consultationId));
    }

    private void setCurrentTenant(UUID lawFirmId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("lawFirmId", lawFirmId.toString())
                .subject("lawyer-a")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
