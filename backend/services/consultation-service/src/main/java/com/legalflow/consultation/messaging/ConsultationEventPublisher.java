package com.legalflow.consultation.messaging;

import com.legalflow.consultation.domain.Consultation;
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
 * Publie les événements du domaine « consultation ». Ne fait jamais échouer une opération métier :
 * toute erreur est journalisée et l'événement est simplement perdu.
 *
 * <p>Confidentialité : ni le sujet, ni la description, ni la réponse de l'avocat ne sont publiés (ils transitent
 * par RabbitMQ puis par email). Les messages sont volontairement génériques.
 */
@Component
public class ConsultationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ConsultationEventPublisher.class);
    private static final String SOURCE = "consultation-service";
    private static final String AGGREGATE_TYPE = "CONSULTATION";

    private final DomainEventPublisher domainEventPublisher;

    public ConsultationEventPublisher(DomainEventPublisher domainEventPublisher) {
        this.domainEventPublisher = domainEventPublisher;
    }

    public void created(Consultation consultation) {
        publish(EventTypes.CONSULTATION_CREATED, consultation, "Nouvelle demande de consultation enregistrée.");
    }

    public void assigned(Consultation consultation) {
        publish(EventTypes.CONSULTATION_ASSIGNED, consultation, "Une consultation vous a été assignée.");
    }

    public void answered(Consultation consultation) {
        publish(EventTypes.CONSULTATION_ANSWERED, consultation, "Votre consultation a reçu une réponse.");
    }

    public void closed(Consultation consultation) {
        publish(EventTypes.CONSULTATION_CLOSED, consultation, "Votre consultation a été clôturée.");
    }

    public void cancelled(Consultation consultation) {
        publish(EventTypes.CONSULTATION_CANCELLED, consultation, "Une consultation a été annulée.");
    }

    private void publish(String eventType, Consultation consultation, String message) {
        try {
            UUID lawFirmId = consultation.getLawFirmId();
            EventRecipient actor = currentActor(lawFirmId);

            Map<String, Object> payload = new HashMap<>();
            if (actor != null) {
                payload.put("actor", actor);
            }
            payload.put("client", clientRecipient(consultation, actor));
            if (consultation.getLawyerId() != null) {
                payload.put("lawyer", lawyerRecipient(consultation, actor));
            }
            payload.put("message", message);

            domainEventPublisher.publish(DomainEventEnvelope.of(eventType, lawFirmId, SOURCE, AGGREGATE_TYPE,
                    consultation.getId(), payload));
        } catch (RuntimeException exception) {
            log.error("Could not publish {} for consultation {}", eventType, consultation.getId(), exception);
        }
    }

    /** Le client est identifié par son {@code clientId} (fiche client), jamais par son compte Keycloak (SEC-07). */
    private EventRecipient clientRecipient(Consultation consultation, EventRecipient actor) {
        UUID clientId = consultation.getClientId();
        if (actor != null && EventRecipient.CLIENT.equals(actor.recipientRole()) && clientId.equals(actor.recipientId())) {
            return EventRecipient.client(clientId, consultation.getLawFirmId(), actor.name(), actor.email(), actor.phone());
        }
        return EventRecipient.client(clientId, consultation.getLawFirmId(), null, null, null);
    }

    /** L'avocat est identifié par son {@code sub} Keycloak : c'est la valeur stockée dans {@code lawyerId}. */
    private EventRecipient lawyerRecipient(Consultation consultation, EventRecipient actor) {
        UUID lawyerId = consultation.getLawyerId();
        if (actor != null && EventRecipient.LAWYER.equals(actor.recipientRole()) && lawyerId.equals(actor.recipientId())) {
            return EventRecipient.lawyer(lawyerId, consultation.getLawFirmId(), actor.name(), actor.email(), actor.phone());
        }
        return EventRecipient.lawyer(lawyerId, consultation.getLawFirmId(), null, null, null);
    }

    /**
     * L'utilisateur connecté : {@code clientId} du token pour un CLIENT, {@code sub} pour le personnel.
     * {@code null} pour un ADMIN (pas de type de destinataire côté consommateur) ou si l'identifiant est invalide.
     */
    private EventRecipient currentActor(UUID lawFirmId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return null;
        }
        Jwt jwt = jwtAuthentication.getToken();
        String name = firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username"));
        String email = jwt.getClaimAsString("email");
        if (hasAuthority(authentication, "ROLE_CLIENT")) {
            UUID clientId = parseUuid(jwt.getClaimAsString("clientId"));
            return clientId == null ? null : EventRecipient.client(clientId, lawFirmId, name, email, null);
        }
        UUID sub = parseUuid(jwt.getSubject());
        if (sub == null) {
            return null;
        }
        if (hasAuthority(authentication, "ROLE_LAWYER")) {
            return EventRecipient.lawyer(sub, lawFirmId, name, email, null);
        }
        if (hasAuthority(authentication, "ROLE_SECRETARY")) {
            return EventRecipient.secretary(sub, lawFirmId, name, email, null);
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
