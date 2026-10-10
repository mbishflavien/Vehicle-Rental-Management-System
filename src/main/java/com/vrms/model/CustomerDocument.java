package com.vrms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * A scan or photo a customer uploaded (driver license, national ID, passport). The metadata lives in
 * this MongoDB collection; the file itself is stored in GridFS and referenced by {@link #fileId}.
 * Staff review it before handing over a vehicle.
 */
@Document(collection = "customer_documents")
public class CustomerDocument {

    public enum Type { DRIVER_LICENSE, NATIONAL_ID, PASSPORT, OTHER }

    public enum Status { PENDING, VERIFIED, REJECTED }

    @Id
    private String documentId;

    /** References customers.customer_id in PostgreSQL. */
    @Indexed
    private UUID customerId;

    private Type type;
    private String fileName;
    private String contentType;
    private long sizeBytes;

    /** GridFS file id. */
    @JsonIgnore
    private String fileId;

    private Status status = Status.PENDING;
    private String uploadedBy;
    private Instant uploadedAt;
    private String reviewedBy;
    private Instant reviewedAt;
    private String reviewNote;

    public String getDocumentId() { return documentId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
}
