package com.vrms.messaging;

import com.vrms.model.ContractStatus;
import com.vrms.model.Customer;
import com.vrms.model.RentalContract;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Called by the services when something happens. Events are first raised inside the current
 * transaction and only relayed to RabbitMQ after it commits (see RabbitEventRelay), so a
 * rolled-back booking never sends an email.
 */
@Component
public class RentalEventPublisher {

    private final ApplicationEventPublisher publisher;

    public RentalEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void contract(RentalEvent.Type type, RentalContract c) {
        Customer customer = c.getCustomer();
        publisher.publishEvent(new RentalEvent(UUID.randomUUID(), type, Instant.now(), c.getContractId(),
                customer.getCustomerId(), customer.getFullName(), customer.getEmail(), customer.getPhoneNumber(),
                c.getVehicle().getModel(), c.getVehicle().getPlateNumber(),
                c.getPickupBranch() == null ? null : c.getPickupBranch().getName(),
                c.getStartDate(), c.getEndDate(), c.getTotalCost(), null));
    }

    /** Event for a contract status change, e.g. PENDING -> ACTIVE is "approved". */
    public void statusChanged(RentalContract c, ContractStatus target) {
        RentalEvent.Type type = switch (target) {
            case ACTIVE -> RentalEvent.Type.CONTRACT_APPROVED;
            case COMPLETED -> RentalEvent.Type.CONTRACT_COMPLETED;
            case CANCELLED -> RentalEvent.Type.CONTRACT_CANCELLED;
            case PENDING -> null;
        };
        if (type != null) {
            contract(type, c);
        }
    }

    public void customer(RentalEvent.Type type, UUID customerId, String name, String email, String phone, String detail) {
        publisher.publishEvent(new RentalEvent(UUID.randomUUID(), type, Instant.now(), null, customerId, name, email,
                phone, null, null, null, null, null, null, detail));
    }
}
