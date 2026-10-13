package com.legalflow.case_service.messaging;

import com.legalflow.case_service.domain.LegalCase;
import com.legalflow.events.DomainEventEnvelope;
import com.legalflow.events.DomainEventPublisher;
import com.legalflow.events.EventRecipient;
import com.legalflow.events.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publie les événements du domaine « dossier ». Ne fait jamais échouer l'ouverture d'un dossier :
 * toute erreur est journalisée et l'événement est simplement perdu.
 */
@Component
public class CaseEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CaseEventPublisher.class);
    private static final String SOURCE = "case-service";
    private static final String AGGREGATE_TYPE = "CASE";

    private final DomainEventPublisher domainEventPublisher;

    public CaseEventPublisher(DomainEventPublisher domainEventPublisher) {
        this.domainEventPublisher = domainEventPublisher;
    }

    /** Prévient l'utilisateur qui ouvre le dossier et l'avocat assigné (clés {@code actor} et {@code lawyer}). */
    public void caseCreated(LegalCase legalCase) {
        try {
            UUID lawFirmId = legalCase.getLawFirmId();
            EventRecipient actor = currentActor(lawFirmId);

            Map<String, Object> payload = new HashMap<>();
            if (actor != null) {
                payload.put("actor", actor);
            }
            payload.put("lawyer", lawyerRecipient(legalCase, actor));
            payload.put("message", "Nouveau dossier ouvert : " + legalCase.getTitle());

            domainEventPublisher.publish(DomainEventEnvelope.of(EventTypes.CASE_CREATED, lawFirmId, SOURCE,
                    AGGREGATE_TYPE, legalCase.getId(), payload));
        } catch (RuntimeException exception) {
            log.error("Could not publish {} for case {}", EventTypes.CASE_CREATED, legalCase.getId(), exception);
        }
    }

    /**
     * L'avocat assigné : son {@code sub} Keycloak est le {@code lawyerId} du dossier. Ses coordonnées ne sont
     * connues que s'il ouvre lui-même le dossier ; sinon la notification lui est rattachée sans email.
     */
    private EventRecipient lawyerRecipient(LegalCase legalCase, EventRecipient actor) {
        UUID lawyerId = legalCase.getLawyerId();
        if (actor != null && lawyerId.equals(actor.recipientId()) && EventRecipient.LAWYER.equals(actor.recipientRole())) {
            return EventRecipient.lawyer(lawyerId, legalCase.getLawFirmId(), actor.name(), actor.email(), actor.phone());
        }
        return EventRecipient.lawyer(lawyerId, legalCase.getLawFirmId(), null, null, null);
    }

    /** L'utilisateur connecté, ou {@code null} s'il n'a pas de rôle notifiable (ADMIN) ou pas de {@code sub} valide. */
    private EventRecipient currentActor(UUID lawFirmId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return null;
        }
        Jwt jwt = jwtAuthentication.getToken();
        UUID sub;
        try {
            sub = UUID.fromString(String.valueOf(jwt.getSubject()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        String name = firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"));
        String email = jwt.getClaimAsString("email");
        if (hasAuthority(authentication, "ROLE_LAWYER")) {
            return EventRecipient.lawyer(sub, lawFirmId, name, email, null);
        }
        if (hasAuthority(authentication, "ROLE_SECRETARY")) {
            return EventRecipient.secretary(sub, lawFirmId, name, email, null);
        }
        return null;
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
