package com.vrms.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology:
 *
 *   vrms.events (topic) ── booking.* | contract.* | customer.* | document.* ──> vrms.notifications.email
 *                       ── booking.requested | contract.* | document.*     ──> vrms.notifications.sms
 *                       ── booking.requested                              ──> vrms.staff.alerts
 *
 * Each queue dead-letters to vrms.events.dlx -> vrms.dead-letter once its retries are used up.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "vrms.events";
    public static final String DEAD_LETTER_EXCHANGE = "vrms.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "vrms.dead-letter";
    public static final String EMAIL_QUEUE = "vrms.notifications.email";
    public static final String SMS_QUEUE = "vrms.notifications.sms";
    public static final String STAFF_ALERTS_QUEUE = "vrms.staff.alerts";

    @Bean
    public TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public FanoutExchange deadLetterExchange() {
        return ExchangeBuilder.fanoutExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE).build();
    }

    @Bean
    public Queue smsQueue() {
        return QueueBuilder.durable(SMS_QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE).build();
    }

    @Bean
    public Queue staffAlertsQueue() {
        return QueueBuilder.durable(STAFF_ALERTS_QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE).build();
    }

    @Bean
    public Declarables bindings(TopicExchange eventsExchange, FanoutExchange deadLetterExchange, Queue deadLetterQueue,
                                Queue emailQueue, Queue smsQueue, Queue staffAlertsQueue) {
        return new Declarables(
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange),
                BindingBuilder.bind(emailQueue).to(eventsExchange).with("booking.*"),
                BindingBuilder.bind(emailQueue).to(eventsExchange).with("contract.*"),
                BindingBuilder.bind(emailQueue).to(eventsExchange).with("customer.*"),
                BindingBuilder.bind(emailQueue).to(eventsExchange).with("document.*"),
                BindingBuilder.bind(smsQueue).to(eventsExchange).with("booking.requested"),
                BindingBuilder.bind(smsQueue).to(eventsExchange).with("contract.*"),
                BindingBuilder.bind(smsQueue).to(eventsExchange).with("document.*"),
                BindingBuilder.bind(staffAlertsQueue).to(eventsExchange).with("booking.requested"));
    }

    /** Messages are JSON, readable in the RabbitMQ management UI. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
