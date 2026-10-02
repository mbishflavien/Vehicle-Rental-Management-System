package com.vrms.model;

public enum ContractStatus {
    /** Booked online by a customer, waiting for staff approval. Vehicle is RESERVED. */
    PENDING,
    /** Vehicle handed over. Vehicle is RENTED. */
    ACTIVE,
    /** Vehicle returned. Vehicle is AVAILABLE again. */
    COMPLETED,
    /** Booking cancelled before completion. Vehicle is AVAILABLE again. */
    CANCELLED
}
