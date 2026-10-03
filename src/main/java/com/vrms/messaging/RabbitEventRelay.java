package com.vrms.messaging;

import com.vrms.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Forwards committed business events to the RabbitMQ topic exchange. */
@Component
public class RabbitEventRelay {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventRelay.class);

    private final RabbitTemplate rabbit;

    public RabbitEventRelay(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void relay(RentalEvent event) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, event.type().routingKey(), event, message -> {
                message.getMessageProperties().setMessageId(event.eventId().toString());
                return message;
            });
            log.debug("Published {} ({})", event.type().routingKey(), event.eventId());
        } catch (AmqpException e) {
            // The business action already succeeded; a broker outage must not undo it.
            log.error("Could not publish {} for {}: {}", event.type(), event.customerEmail(), e.getMessage());
        }
    }
}
