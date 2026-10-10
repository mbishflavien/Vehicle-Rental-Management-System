package com.vrms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One entry in the System Logs: who did what, and when. Stored in MongoDB: an append-only stream
 * of operational events with a loose, growing shape that is never joined or updated.
 */
@Document(collection = "audit_logs")
@CompoundIndex(name = "contract_activity_idx", def = "{'plateNumber': 1, 'timestamp': -1}", sparse = true)
public class AuditLog {

    @Id
    private String logId;

    @Indexed(name = "timestamp_desc", direction = IndexDirection.DESCENDING)
    private Instant timestamp;

    /** Short event name, e.g. "Contract created". */
    @Indexed
    private String event;

    /** Name of the user who triggered the event, or "System". */
    private String actor;

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

    public String getLogId() { return logId; }
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
