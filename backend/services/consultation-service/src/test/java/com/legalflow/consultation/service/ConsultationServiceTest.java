package com.legalflow.consultation.service;

import com.legalflow.consultation.domain.Consultation;
import com.legalflow.consultation.domain.ConsultationStatus;
import com.legalflow.consultation.dto.AnswerConsultationRequest;
import com.legalflow.consultation.dto.AssignConsultationRequest;
import com.legalflow.consultation.dto.CreateConsultationRequest;
import com.legalflow.consultation.dto.UpdateConsultationRequest;
import com.legalflow.consultation.integration.ClientServiceOwnershipVerifier;
import com.legalflow.consultation.messaging.ConsultationEventPublisher;
import com.legalflow.consultation.repository.ConsultationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultationServiceTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_CLIENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID LAWYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_LAWYER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SECRETARY_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID CONSULTATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private ConsultationRepository repository;
    private ClientServiceOwnershipVerifier verifier;
    private ConsultationEventPublisher publisher;
    private ConsultationService service;

    @BeforeEach
    void setUp() {
        repository = mock(ConsultationRepository.class);
        verifier = mock(ClientServiceOwnershipVerifier.class);
        publisher = mock(ConsultationEventPublisher.class);
        service = new ConsultationService(repository, verifier, publisher);
        when(repository.save(any(Consultation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void officeStaffCreatesConsultationForAClientAndVerifiesIt() {
        authenticate(SECRETARY_ID, null, "SECRETARY");

        Consultation result = service.createConsultation(createRequest(CLIENT_ID), null);

        assertEquals(FIRM_A, result.getLawFirmId());
        assertEquals(CLIENT_ID, result.getClientId());
        assertEquals(ConsultationStatus.PENDING, result.getStatus());
        verify(verifier).verifyClientBelongsToCurrentTenant(CLIENT_ID);
    }

    @Test
    void clientCreatesConsultationForThemselvesOnly() {
        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");

        Consultation result = service.createConsultation(createRequest(null), null);
        assertEquals(CLIENT_ID, result.getClientId());
        verify(verifier).verifyClientBelongsToCurrentTenant(CLIENT_ID);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createConsultation(createRequest(OTHER_CLIENT_ID), null));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void invalidTransitionThrowsBusinessException() {
        authenticate(SECRETARY_ID, null, "SECRETARY");
        Consultation consultation = consultation(CLIENT_ID, null, ConsultationStatus.ANSWERED);
        consultation.setAnsweredAt(LocalDateTime.now());
        stub(consultation);

        AssignConsultationRequest assignRequest = new AssignConsultationRequest();
        assignRequest.setLawyerId(LAWYER_ID);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.assignConsultation(CONSULTATION_ID, assignRequest));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void getConsultationRejectsMissingTenantMatch() {
        authenticate(SECRETARY_ID, null, "SECRETARY");
        when(repository.findByIdAndLawFirmId(CONSULTATION_ID, FIRM_A)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.getConsultation(CONSULTATION_ID));
    }

    @Test
    void clientCannotSeeAnotherClientsConsultation() {
        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");

        stub(consultation(OTHER_CLIENT_ID, null, ConsultationStatus.PENDING));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getConsultation(CONSULTATION_ID));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        stub(consultation(CLIENT_ID, null, ConsultationStatus.PENDING));
        assertNotNull(service.getConsultation(CONSULTATION_ID));
    }

    @Test
    void lawyerSeesPendingRequestsAndOwnAssignmentsOnly() {
        authenticate(LAWYER_ID, null, "LAWYER");

        stub(consultation(CLIENT_ID, null, ConsultationStatus.PENDING));
        assertNotNull(service.getConsultation(CONSULTATION_ID));

        stub(consultation(CLIENT_ID, OTHER_LAWYER_ID, ConsultationStatus.ASSIGNED));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getConsultation(CONSULTATION_ID));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        stub(consultation(CLIENT_ID, LAWYER_ID, ConsultationStatus.ASSIGNED));
        assertNotNull(service.getConsultation(CONSULTATION_ID));
    }

    @Test
    void listingDependsOnTheRole() {
        authenticate(SECRETARY_ID, null, "SECRETARY");
        service.getConsultations(null);
        verify(repository).findAllByLawFirmIdOrderByCreatedAtDesc(FIRM_A);

        authenticate(LAWYER_ID, null, "LAWYER");
        service.getConsultations(null);
        verify(repository).findVisibleToLawyer(FIRM_A, LAWYER_ID);

        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        service.getConsultations(null);
        verify(repository).findAllByLawFirmIdAndClientIdOrderByCreatedAtDesc(FIRM_A, CLIENT_ID);
    }

    @Test
    void lawyerCanOnlyAssignConsultationToThemselves() {
        authenticate(LAWYER_ID, null, "LAWYER");
        Consultation consultation = consultation(CLIENT_ID, null, ConsultationStatus.PENDING);
        stub(consultation);

        AssignConsultationRequest forSomeoneElse = new AssignConsultationRequest();
        forSomeoneElse.setLawyerId(OTHER_LAWYER_ID);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.assignConsultation(CONSULTATION_ID, forSomeoneElse));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        AssignConsultationRequest forMyself = new AssignConsultationRequest();
        forMyself.setLawyerId(LAWYER_ID);
        Consultation result = service.assignConsultation(CONSULTATION_ID, forMyself);
        assertEquals(ConsultationStatus.ASSIGNED, result.getStatus());
        assertEquals(LAWYER_ID, result.getLawyerId());
    }

    @Test
    void officeStaffCanAssignAnyLawyer() {
        authenticate(SECRETARY_ID, null, "SECRETARY");
        stub(consultation(CLIENT_ID, null, ConsultationStatus.PENDING));

        AssignConsultationRequest request = new AssignConsultationRequest();
        request.setLawyerId(LAWYER_ID);
        Consultation result = service.assignConsultation(CONSULTATION_ID, request);

        assertEquals(ConsultationStatus.ASSIGNED, result.getStatus());
        assertEquals(LAWYER_ID, result.getLawyerId());
    }

    @Test
    void onlyTheAssignedLawyerCanStartAndAnswer() {
        Consultation consultation = consultation(CLIENT_ID, LAWYER_ID, ConsultationStatus.ASSIGNED);
        stub(consultation);

        authenticate(SECRETARY_ID, null, "SECRETARY");
        ResponseStatusException notLawyer = assertThrows(ResponseStatusException.class,
                () -> service.startConsultation(CONSULTATION_ID));
        assertEquals(HttpStatus.FORBIDDEN, notLawyer.getStatusCode());

        authenticate(OTHER_LAWYER_ID, null, "LAWYER");
        ResponseStatusException otherLawyer = assertThrows(ResponseStatusException.class,
                () -> service.startConsultation(CONSULTATION_ID));
        assertEquals(HttpStatus.NOT_FOUND, otherLawyer.getStatusCode());

        authenticate(LAWYER_ID, null, "LAWYER");
        assertEquals(ConsultationStatus.IN_PROGRESS, service.startConsultation(CONSULTATION_ID).getStatus());

        AnswerConsultationRequest answer = new AnswerConsultationRequest();
        answer.setAnswer("Voici ma réponse détaillée.");
        Consultation answered = service.answerConsultation(CONSULTATION_ID, answer);
        assertEquals(ConsultationStatus.ANSWERED, answered.getStatus());
        assertEquals("Voici ma réponse détaillée.", answered.getAnswer());
        assertNotNull(answered.getAnsweredAt());

        assertEquals(ConsultationStatus.CLOSED, service.closeConsultation(CONSULTATION_ID).getStatus());
    }

    @Test
    void clientCanEditOnlyItsOwnPendingConsultation() {
        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        UpdateConsultationRequest request = new UpdateConsultationRequest();
        request.setSubject("Nouveau sujet");
        request.setDescription("Nouvelle description détaillée");

        stub(consultation(CLIENT_ID, null, ConsultationStatus.PENDING));
        assertEquals("Nouveau sujet", service.updateConsultation(CONSULTATION_ID, request).getSubject());

        stub(consultation(CLIENT_ID, LAWYER_ID, ConsultationStatus.ASSIGNED));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateConsultation(CONSULTATION_ID, request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void clientCanCancelItsOwnConsultation() {
        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        stub(consultation(CLIENT_ID, null, ConsultationStatus.PENDING));

        assertEquals(ConsultationStatus.CANCELLED, service.cancelConsultation(CONSULTATION_ID).getStatus());
    }

    @Test
    void lawyerCannotCancelOrDeleteConsultations() {
        authenticate(LAWYER_ID, null, "LAWYER");
        stub(consultation(CLIENT_ID, LAWYER_ID, ConsultationStatus.ASSIGNED));

        ResponseStatusException cancel = assertThrows(ResponseStatusException.class,
                () -> service.cancelConsultation(CONSULTATION_ID));
        assertEquals(HttpStatus.FORBIDDEN, cancel.getStatusCode());

        ResponseStatusException delete = assertThrows(ResponseStatusException.class,
                () -> service.deleteConsultation(CONSULTATION_ID));
        assertEquals(HttpStatus.FORBIDDEN, delete.getStatusCode());
    }

    @Test
    void creatingAConsultationPublishesCreated() {
        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");

        Consultation result = service.createConsultation(createRequest(null), null);

        verify(publisher).created(result);
    }

    @Test
    void eachSuccessfulTransitionPublishesItsEvent() {
        stub(consultation(CLIENT_ID, null, ConsultationStatus.PENDING));

        authenticate(SECRETARY_ID, null, "SECRETARY");
        AssignConsultationRequest assign = new AssignConsultationRequest();
        assign.setLawyerId(LAWYER_ID);
        verify(publisher, never()).assigned(any(Consultation.class));
        service.assignConsultation(CONSULTATION_ID, assign);
        verify(publisher).assigned(any(Consultation.class));

        authenticate(LAWYER_ID, null, "LAWYER");
        service.startConsultation(CONSULTATION_ID);
        AnswerConsultationRequest answer = new AnswerConsultationRequest();
        answer.setAnswer("Voici ma réponse détaillée.");
        service.answerConsultation(CONSULTATION_ID, answer);
        verify(publisher).answered(any(Consultation.class));

        service.closeConsultation(CONSULTATION_ID);
        verify(publisher).closed(any(Consultation.class));
    }

    @Test
    void cancellingAndDeletingPublishCancelledButARejectedCancelDoesNot() {
        authenticate(LAWYER_ID, null, "LAWYER");
        stub(consultation(CLIENT_ID, LAWYER_ID, ConsultationStatus.ASSIGNED));
        assertThrows(ResponseStatusException.class, () -> service.cancelConsultation(CONSULTATION_ID));
        verify(publisher, never()).cancelled(any(Consultation.class));

        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        service.cancelConsultation(CONSULTATION_ID);
        verify(publisher).cancelled(any(Consultation.class));
    }

    @Test
    void deletingAConsultationPublishesCancelled() {
        authenticate(SECRETARY_ID, null, "SECRETARY");
        stub(consultation(CLIENT_ID, null, ConsultationStatus.PENDING));

        service.deleteConsultation(CONSULTATION_ID);

        verify(publisher).cancelled(any(Consultation.class));
    }

    private void stub(Consultation consultation) {
        when(repository.findByIdAndLawFirmId(CONSULTATION_ID, FIRM_A)).thenReturn(Optional.of(consultation));
    }

    private Consultation consultation(UUID clientId, UUID lawyerId, ConsultationStatus status) {
        return new Consultation(FIRM_A, clientId, lawyerId, "Question", "Need legal advice", status, null);
    }

    private CreateConsultationRequest createRequest(UUID clientId) {
        CreateConsultationRequest request = new CreateConsultationRequest();
        request.setClientId(clientId);
        request.setSubject("Question");
        request.setDescription("Need legal advice");
        return request;
    }

    private void authenticate(UUID userId, UUID clientId, String... roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("lawFirmId", FIRM_A.toString())
                .subject(userId.toString());
        if (clientId != null) {
            builder.claim("clientId", clientId.toString());
        }
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), authorities));
    }
}
