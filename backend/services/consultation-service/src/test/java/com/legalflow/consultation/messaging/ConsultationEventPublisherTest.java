package com.legalflow.consultation.messaging;

import com.legalflow.consultation.domain.Consultation;
import com.legalflow.consultation.domain.ConsultationStatus;
import com.legalflow.events.DomainEventEnvelope;
import com.legalflow.events.DomainEventPublisher;
import com.legalflow.events.EventRecipient;
import com.legalflow.events.EventTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConsultationEventPublisherTest {

    private static final UUID FIRM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CONSULTATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CLIENT_ACCOUNT_SUB = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID LAWYER_SUB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SECRETARY_SUB = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final ConsultationEventPublisher publisher = new ConsultationEventPublisher(domainEventPublisher);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientIsIdentifiedByClientIdAndNeverByItsKeycloakAccount() {
        authenticateClient(CLIENT_ACCOUNT_SUB, CLIENT_ID.toString());

        publisher.created(consultation(null, ConsultationStatus.PENDING));

        DomainEventEnvelope event = publishedEvent();
        assertEquals(EventTypes.CONSULTATION_CREATED, event.eventType());
        assertEquals(CONSULTATION_ID, event.aggregateId());
        assertEquals("consultation-service", event.source());
        EventRecipient actor = (EventRecipient) event.payload().get("actor");
        EventRecipient client = (EventRecipient) event.payload().get("client");
        assertEquals(EventRecipient.CLIENT, actor.recipientRole());
        assertEquals(CLIENT_ID, actor.recipientId());
        assertEquals(CLIENT_ID, client.recipientId());
        assertFalse(event.payload().toString().contains(CLIENT_ACCOUNT_SUB.toString()));
    }

    @Test
    void secretaryAssigningNotifiesTheLawyerByKeycloakSub() {
        authenticate("ROLE_SECRETARY", SECRETARY_SUB.toString());

        publisher.assigned(consultation(LAWYER_SUB, ConsultationStatus.ASSIGNED));

        DomainEventEnvelope event = publishedEvent();
        assertEquals(EventTypes.CONSULTATION_ASSIGNED, event.eventType());
        EventRecipient actor = (EventRecipient) event.payload().get("actor");
        EventRecipient lawyer = (EventRecipient) event.payload().get("lawyer");
        assertEquals(SECRETARY_SUB, actor.recipientId());
        assertEquals(EventRecipient.SECRETARY, actor.recipientRole());
        assertEquals(LAWYER_SUB, lawyer.recipientId());
        assertEquals(EventRecipient.LAWYER, lawyer.recipientRole());
    }

    @Test
    void lawyerAnswerNotifiesTheClientByClientIdWithoutLeakingTheAnswer() {
        authenticate("ROLE_LAWYER", LAWYER_SUB.toString());
        Consultation answered = consultation(LAWYER_SUB, ConsultationStatus.ANSWERED);
        answered.setAnswer("Réponse confidentielle de l'avocat");

        publisher.answered(answered);

        DomainEventEnvelope event = publishedEvent();
        assertEquals(EventTypes.CONSULTATION_ANSWERED, event.eventType());
        EventRecipient client = (EventRecipient) event.payload().get("client");
        assertEquals(CLIENT_ID, client.recipientId());
        assertEquals(EventRecipient.CLIENT, client.recipientRole());
        assertNull(client.email());
        assertFalse(event.payload().toString().contains("confidentielle"));
        assertFalse(event.payload().toString().contains("Need legal advice"));
        assertFalse(event.payload().toString().contains("Question"));
    }

    @Test
    void clientCancellingIsBothActorAndClientRecipient() {
        authenticateClient(CLIENT_ACCOUNT_SUB, CLIENT_ID.toString());

        publisher.cancelled(consultation(null, ConsultationStatus.CANCELLED));

        DomainEventEnvelope event = publishedEvent();
        assertEquals(EventTypes.CONSULTATION_CANCELLED, event.eventType());
        assertEquals(CLIENT_ID, ((EventRecipient) event.payload().get("actor")).recipientId());
        assertEquals("client@example.test", ((EventRecipient) event.payload().get("client")).email());
        assertFalse(event.payload().containsKey("lawyer"));
    }

    @Test
    void adminActorIsOmittedAndClientIsStillNotified() {
        authenticate("ROLE_ADMIN", UUID.randomUUID().toString());

        publisher.closed(consultation(LAWYER_SUB, ConsultationStatus.CLOSED));

        DomainEventEnvelope event = publishedEvent();
        assertFalse(event.payload().containsKey("actor"));
        assertEquals(CLIENT_ID, ((EventRecipient) event.payload().get("client")).recipientId());
    }

    @Test
    void clientTokenWithoutValidClientIdHasNoActorButClientIsStillNotified() {
        authenticateClient(CLIENT_ACCOUNT_SUB, "not-a-uuid");

        publisher.created(consultation(null, ConsultationStatus.PENDING));

        DomainEventEnvelope event = publishedEvent();
        assertFalse(event.payload().containsKey("actor"));
        assertEquals(CLIENT_ID, ((EventRecipient) event.payload().get("client")).recipientId());
    }

    @Test
    void publishingFailureNeverBreaksTheBusinessOperation() {
        authenticate("ROLE_LAWYER", LAWYER_SUB.toString());
        doThrow(new IllegalStateException("broker down")).when(domainEventPublisher)
                .publish(any(DomainEventEnvelope.class));

        assertDoesNotThrow(() -> publisher.answered(consultation(LAWYER_SUB, ConsultationStatus.ANSWERED)));
    }

    @Test
    void nothingIsPublishedWhenTheConsultationHasNoId() {
        authenticate("ROLE_LAWYER", LAWYER_SUB.toString());
        Consultation unsaved = new Consultation(FIRM, CLIENT_ID, LAWYER_SUB, "Question", "Need legal advice",
                ConsultationStatus.ANSWERED, null);

        assertDoesNotThrow(() -> publisher.answered(unsaved));

        verify(domainEventPublisher, never()).publish(any(DomainEventEnvelope.class));
    }

    private DomainEventEnvelope publishedEvent() {
        ArgumentCaptor<DomainEventEnvelope> captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(domainEventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private Consultation consultation(UUID lawyerId, ConsultationStatus status) {
        Consultation consultation = new Consultation(FIRM, CLIENT_ID, lawyerId, "Question", "Need legal advice",
                status, null);
        ReflectionTestUtils.setField(consultation, "id", CONSULTATION_ID);
        return consultation;
    }

    private void authenticateClient(UUID accountSub, String clientIdClaim) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject(accountSub.toString())
                .claim("lawFirmId", FIRM.toString())
                .claim("clientId", clientIdClaim)
                .claim("name", "Client A")
                .claim("email", "client@example.test")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))));
    }

    private void authenticate(String role, String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject(subject)
                .claim("lawFirmId", FIRM.toString())
                .claim("name", "Maître A")
                .claim("email", "staff@example.test")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role))));
    }
}
