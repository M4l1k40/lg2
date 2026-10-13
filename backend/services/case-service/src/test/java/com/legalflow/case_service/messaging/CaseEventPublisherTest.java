package com.legalflow.case_service.messaging;

import com.legalflow.case_service.domain.CaseStatus;
import com.legalflow.case_service.domain.LegalCase;
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

class CaseEventPublisherTest {

    private static final UUID FIRM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CASE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LAWYER_SUB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SECRETARY_SUB = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID CLIENT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final CaseEventPublisher caseEventPublisher = new CaseEventPublisher(domainEventPublisher);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lawyerOpeningHisOwnCaseIsBothActorAndAssignedLawyer() {
        authenticate("ROLE_LAWYER", LAWYER_SUB.toString());

        caseEventPublisher.caseCreated(legalCase());

        DomainEventEnvelope event = publishedEvent();
        assertEquals(EventTypes.CASE_CREATED, event.eventType());
        assertEquals(FIRM, event.lawFirmId());
        assertEquals(CASE_ID, event.aggregateId());
        assertEquals("case-service", event.source());
        EventRecipient actor = (EventRecipient) event.payload().get("actor");
        EventRecipient lawyer = (EventRecipient) event.payload().get("lawyer");
        assertEquals(LAWYER_SUB, actor.recipientId());
        assertEquals(EventRecipient.LAWYER, actor.recipientRole());
        assertEquals("avocat@example.test", lawyer.email());
        assertEquals(LAWYER_SUB, lawyer.recipientId());
    }

    @Test
    void secretaryOpeningACaseNotifiesTheAssignedLawyerByKeycloakSub() {
        authenticate("ROLE_SECRETARY", SECRETARY_SUB.toString());

        caseEventPublisher.caseCreated(legalCase());

        DomainEventEnvelope event = publishedEvent();
        EventRecipient actor = (EventRecipient) event.payload().get("actor");
        EventRecipient lawyer = (EventRecipient) event.payload().get("lawyer");
        assertEquals(SECRETARY_SUB, actor.recipientId());
        assertEquals(EventRecipient.SECRETARY, actor.recipientRole());
        assertEquals(LAWYER_SUB, lawyer.recipientId());
        assertNull(lawyer.email());
    }

    @Test
    void adminActorIsOmittedBecauseTheConsumerHasNoAdminRecipientType() {
        authenticate("ROLE_ADMIN", UUID.randomUUID().toString());

        caseEventPublisher.caseCreated(legalCase());

        DomainEventEnvelope event = publishedEvent();
        assertFalse(event.payload().containsKey("actor"));
        assertEquals(LAWYER_SUB, ((EventRecipient) event.payload().get("lawyer")).recipientId());
    }

    @Test
    void clientPersonalDataIsNeverPublished() {
        authenticate("ROLE_LAWYER", LAWYER_SUB.toString());

        caseEventPublisher.caseCreated(legalCase());

        DomainEventEnvelope event = publishedEvent();
        assertFalse(event.payload().containsKey("client"));
        assertFalse(event.payload().toString().contains(CLIENT_ID.toString()));
    }

    @Test
    void tokenWithoutUuidSubjectStillNotifiesTheAssignedLawyer() {
        authenticate("ROLE_SECRETARY", "not-a-uuid");

        caseEventPublisher.caseCreated(legalCase());

        DomainEventEnvelope event = publishedEvent();
        assertFalse(event.payload().containsKey("actor"));
        assertEquals(LAWYER_SUB, ((EventRecipient) event.payload().get("lawyer")).recipientId());
    }

    @Test
    void publishingFailureNeverBreaksCaseCreation() {
        authenticate("ROLE_LAWYER", LAWYER_SUB.toString());
        doThrow(new IllegalStateException("broker down")).when(domainEventPublisher)
                .publish(any(DomainEventEnvelope.class));

        assertDoesNotThrow(() -> caseEventPublisher.caseCreated(legalCase()));
    }

    @Test
    void nothingIsPublishedWhenTheCaseHasNoId() {
        authenticate("ROLE_LAWYER", LAWYER_SUB.toString());
        LegalCase unsaved = new LegalCase(FIRM, CLIENT_ID, LAWYER_SUB, "CASE-1", "Titre", "Desc", CaseStatus.OPEN);

        assertDoesNotThrow(() -> caseEventPublisher.caseCreated(unsaved));

        verify(domainEventPublisher, never()).publish(any(DomainEventEnvelope.class));
    }

    private DomainEventEnvelope publishedEvent() {
        ArgumentCaptor<DomainEventEnvelope> captor = ArgumentCaptor.forClass(DomainEventEnvelope.class);
        verify(domainEventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private LegalCase legalCase() {
        LegalCase legalCase = new LegalCase(FIRM, CLIENT_ID, LAWYER_SUB, "CASE-1", "Divorce Martin", "Desc",
                CaseStatus.OPEN);
        ReflectionTestUtils.setField(legalCase, "id", CASE_ID);
        return legalCase;
    }

    private void authenticate(String role, String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject(subject)
                .claim("lawFirmId", FIRM.toString())
                .claim("name", "Maître A")
                .claim("email", "avocat@example.test")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role))));
    }
}
