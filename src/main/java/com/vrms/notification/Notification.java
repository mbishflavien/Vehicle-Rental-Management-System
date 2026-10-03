package com.vrms.notification;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/** Record of every email and SMS VRMS sent (or tried to send), kept in MongoDB. */
@Document(collection = "notifications")
public class Notification {

    public enum Channel { EMAIL, SMS }

    public enum Status { SENT, SIMULATED, FAILED }

    @Id
    private String notificationId;

    @Indexed(direction = IndexDirection.DESCENDING)
    private Instant createdAt;

    /** RabbitMQ event that caused it; with the channel and recipient, makes redelivery idempotent. */
    @Indexed
    private UUID eventId;
    private String eventType;
    private Channel channel;
    private String recipient;
    private String subject;
    private String body;
    private Status status;
    private String provider;
    private String error;

    @Indexed(sparse = true)
    private UUID customerId;
    private UUID contractId;

    public String getNotificationId() { return notificationId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getContractId() { return contractId; }
    public void setContractId(UUID contractId) { this.contractId = contractId; }
}
