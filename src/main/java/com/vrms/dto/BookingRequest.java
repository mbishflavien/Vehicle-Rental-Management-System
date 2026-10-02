package com.vrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Customer "Reserve & book" from the public fleet page. */
public record BookingRequest(
        @NotNull(message = "Choose a vehicle") UUID vehicleId,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "Return date is required") LocalDate endDate,
        @Size(max = 100) String pickupLocation) {
}
