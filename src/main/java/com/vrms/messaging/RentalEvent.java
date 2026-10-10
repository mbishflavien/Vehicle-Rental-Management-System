package com.vrms.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A business event published to RabbitMQ (JSON). It carries everything the email/SMS consumers
 * need, so they never have to query the database.
 */
public record RentalEvent(
        UUID eventId,
        Type type,
        Instant occurredAt,
        UUID contractId,
        UUID customerId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String vehicleModel,
        String plateNumber,
        String pickupBranch,
        LocalDate startDate,
        LocalDate endDate,
        Double totalCost,
        /** Free text for events that need it, e.g. a document review note. */
        String detail) {

    public enum Type {
        BOOKING_REQUESTED("booking.requested"),
        CONTRACT_ISSUED("contract.issued"),
        CONTRACT_APPROVED("contract.approved"),
        CONTRACT_COMPLETED("contract.completed"),
        CONTRACT_CANCELLED("contract.cancelled"),
        CUSTOMER_REGISTERED("customer.registered"),
        DOCUMENT_VERIFIED("document.verified"),
        DOCUMENT_REJECTED("document.rejected");

        private final String routingKey;

        Type(String routingKey) {
            this.routingKey = routingKey;
        }

        public String routingKey() {
            return routingKey;
        }
    }
}
