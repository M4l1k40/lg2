package com.legalflow.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitNotificationConfig {

    public static final String EVENTS_EXCHANGE = "legalflow.events";
    public static final String NOTIFICATION_QUEUE = "legalflow.notification.queue";
    public static final String NOTIFICATION_DLX = "legalflow.notification.dlx";
    public static final String NOTIFICATION_DLQ = "legalflow.notification.dlq";
    public static final String NOTIFICATION_DLQ_ROUTING_KEY = "notification.failed";

    @Bean
    public Declarables notificationRabbitDeclarables() {
        TopicExchange eventsExchange = new TopicExchange(EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(NOTIFICATION_DLX, true, false);
        Queue notificationQueue = QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(NOTIFICATION_DLX)
                .deadLetterRoutingKey(NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(NOTIFICATION_DLQ).build();
        Binding eventsBinding = BindingBuilder.bind(notificationQueue).to(eventsExchange).with("#");
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange).with(NOTIFICATION_DLQ_ROUTING_KEY);
        return new Declarables(eventsExchange, deadLetterExchange, notificationQueue,
                deadLetterQueue, eventsBinding, deadLetterBinding);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}