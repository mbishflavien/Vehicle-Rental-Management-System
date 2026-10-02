package com.vrms.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** One row in the System Logs page: who did what, and when. */
@Entity
@Table(name = "audit_logs", indexes = @Index(name = "idx_audit_logs_timestamp", columnList = "timestamp"))
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID logId;

    @Column(nullable = false)
    private Instant timestamp;

    /** Short event name, e.g. "Contract created". */
    @Column(nullable = false)
    private String event;

    /** Name of the user who triggered the event, or "System". */
    @Column(nullable = false)
    private String actor;

    @Column(length = 500)
    private String details;

    /** Optional amount (RWF) for money-related events, shown on the dashboard. */
    private Double amount;

    /** Optional plate number, shown on the dashboard. */
    private String plateNumber;

    /** Optional customer name, shown on the dashboard. */
    private String customerName;

    public AuditLog() {}

    public AuditLog(String event, String actor, String details) {
        this.timestamp = Instant.now();
        this.event = event;
        this.actor = actor;
        this.details = details;
    }

    public UUID getLogId() { return logId; }
    public Instant getTimestamp() { return timestamp; }
    public String getEvent() { return event; }
    public String getActor() { return actor; }
    public String getDetails() { return details; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
}
