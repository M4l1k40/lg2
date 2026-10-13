package com.legalflow.events;

/** Noms RabbitMQ partagés entre publishers et consommateurs. */
public final class EventsTopology {

    public static final String EVENTS_EXCHANGE = "legalflow.events";

    private EventsTopology() {
    }
}
