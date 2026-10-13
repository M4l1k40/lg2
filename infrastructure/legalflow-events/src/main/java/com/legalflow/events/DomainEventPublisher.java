package com.legalflow.events;

/** Point d'entrée unique pour publier un événement de domaine. */
public interface DomainEventPublisher {

    /**
     * Publie l'événement. Dans une transaction active, l'envoi n'a lieu qu'<b>après le commit</b> ; en cas de
     * rollback, rien n'est envoyé. Une panne du broker ne fait jamais échouer l'appelant (elle est journalisée).
     */
    void publish(DomainEventEnvelope event);
}
