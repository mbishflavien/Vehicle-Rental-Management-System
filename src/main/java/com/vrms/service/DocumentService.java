package com.vrms.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.vrms.exception.ApiException;
import com.vrms.messaging.RentalEvent;
import com.vrms.messaging.RentalEventPublisher;
import com.vrms.model.Customer;
import com.vrms.model.CustomerDocument;
import com.vrms.repository.CustomerDocumentRepository;
import com.vrms.repository.CustomerRepository;
import com.vrms.security.CurrentUser;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Customer identity documents: metadata in MongoDB, files in GridFS. */
@Service
public class DocumentService {

    static final long MAX_BYTES = 5 * 1024 * 1024;

    /** Allowed types, checked against the file's first bytes rather than trusting the client. */
    private static final Map<String, byte[]> SIGNATURES = Map.of(
            "application/pdf", new byte[]{'%', 'P', 'D', 'F'},
            "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'},
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    private final CustomerDocumentRepository repository;
    private final GridFsTemplate gridFs;
    private final AuditService audit;
    private final CustomerRepository customers;
    private final RentalEventPublisher events;

    public DocumentService(CustomerDocumentRepository repository, GridFsTemplate gridFs, AuditService audit,
                           CustomerRepository customers, RentalEventPublisher events) {
        this.repository = repository;
        this.gridFs = gridFs;
        this.audit = audit;
        this.customers = customers;
        this.events = events;
    }

    public List<CustomerDocument> forCustomer(UUID customerId) {
        return repository.findByCustomerIdOrderByUploadedAtDesc(customerId);
    }

    public CustomerDocument get(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
    }

    public CustomerDocument upload(Customer customer, CustomerDocument.Type type, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a file to upload", "file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Files must be 5 MB or smaller", "file");
        }
        String contentType = detectType(file);
        if (repository.countByCustomerId(customer.getCustomerId()) >= 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can keep up to 10 documents. Remove one first.", "file");
        }

        String name = sanitize(file.getOriginalFilename());
        ObjectId fileId;
        try (InputStream in = file.getInputStream()) {
            fileId = gridFs.store(in, name, contentType,
                    new Document("customerId", customer.getCustomerId().toString()).append("type", type.name()));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file could not be read", "file");
        }

        CustomerDocument doc = new CustomerDocument();
        doc.setCustomerId(customer.getCustomerId());
        doc.setType(type);
        doc.setFileName(name);
        doc.setContentType(contentType);
        doc.setSizeBytes(file.getSize());
        doc.setFileId(fileId.toHexString());
        doc.setUploadedBy(CurrentUser.displayName());
        doc.setUploadedAt(Instant.now());
        CustomerDocument saved = repository.save(doc);
        audit.log("Document uploaded", pretty(type) + " uploaded for " + customer.getFullName());
        return saved;
    }

    public InputStreamResource content(CustomerDocument doc) {
        GridFSFile file = gridFs.findOne(Query.query(Criteria.where("_id").is(new ObjectId(doc.getFileId()))));
        if (file == null) {
            throw ApiException.notFound("File");
        }
        try {
            return new InputStreamResource(gridFs.getResource(file).getInputStream());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The file could not be read");
        }
    }

    public CustomerDocument review(String id, CustomerDocument.Status status, String note) {
        if (status == CustomerDocument.Status.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose verified or rejected", "status");
        }
        CustomerDocument doc = get(id);
        doc.setStatus(status);
        doc.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        doc.setReviewedBy(CurrentUser.displayName());
        doc.setReviewedAt(Instant.now());
        CustomerDocument saved = repository.save(doc);
        audit.log("Document reviewed", pretty(doc.getType()) + " marked " + status.name().toLowerCase());
        customers.findById(doc.getCustomerId()).ifPresent(c -> events.customer(
                status == CustomerDocument.Status.VERIFIED ? RentalEvent.Type.DOCUMENT_VERIFIED : RentalEvent.Type.DOCUMENT_REJECTED,
                c.getCustomerId(), c.getFullName(), c.getEmail(), c.getPhoneNumber(), pretty(doc.getType()).toLowerCase()));
        return saved;
    }

    public void delete(CustomerDocument doc) {
        gridFs.delete(Query.query(Criteria.where("_id").is(new ObjectId(doc.getFileId()))));
        repository.delete(doc);
        audit.log("Document removed", pretty(doc.getType()) + " (" + doc.getFileName() + ") removed");
    }

    /** Removes every document of a customer who is being deleted. */
    public void deleteAllFor(UUID customerId) {
        forCustomer(customerId).forEach(d -> {
            gridFs.delete(Query.query(Criteria.where("_id").is(new ObjectId(d.getFileId()))));
            repository.delete(d);
        });
    }

    private static String detectType(MultipartFile file) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(8);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file could not be read", "file");
        }
        return SIGNATURES.entrySet().stream()
                .filter(e -> head.length >= e.getValue().length
                        && Arrays.equals(Arrays.copyOf(head, e.getValue().length), e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Upload a PDF, JPEG or PNG file", "file"));
    }

    private static String sanitize(String name) {
        String base = name == null || name.isBlank() ? "document" : name.replaceAll("[\\\\/]", "_");
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    private static String pretty(CustomerDocument.Type type) {
        return switch (type) {
            case DRIVER_LICENSE -> "Driver license";
            case NATIONAL_ID -> "National ID";
            case PASSPORT -> "Passport";
            case OTHER -> "Document";
        };
    }
}
