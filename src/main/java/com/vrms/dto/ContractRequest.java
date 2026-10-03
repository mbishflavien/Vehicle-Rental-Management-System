package com.vrms.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** Staff "Issue new contract" modal. */
public record ContractRequest(
        @NotNull(message = "Choose a customer") UUID customerId,
        @NotNull(message = "Choose a vehicle") UUID vehicleId,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "Return date is required") LocalDate endDate,
        /** Optional; defaults to the vehicle's home branch. */
        UUID pickupBranchId) {
}
