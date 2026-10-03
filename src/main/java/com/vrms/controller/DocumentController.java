package com.vrms.controller;

import com.vrms.exception.ApiException;
import com.vrms.model.Customer;
import com.vrms.model.CustomerDocument;
import com.vrms.model.User;
import com.vrms.repository.CustomerRepository;
import com.vrms.security.CurrentUser;
import com.vrms.service.CustomerService;
import com.vrms.service.DocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Customer identity documents stored in MongoDB GridFS. */
@RestController
public class DocumentController {

    public record ReviewRequest(@NotNull(message = "Choose verified or rejected") CustomerDocument.Status status,
                                @Size(max = 300) String note) {}

    private final DocumentService documents;
    private final CustomerService customers;
    private final CustomerRepository customerRepository;

    public DocumentController(DocumentService documents, CustomerService customers, CustomerRepository customerRepository) {
        this.documents = documents;
        this.customers = customers;
        this.customerRepository = customerRepository;
    }

    // --- Customers: their own documents ------------------------------------------------------------

    @GetMapping("/api/me/documents")
    @PreAuthorize("hasAuthority('BOOKING_OWN')")
    public List<CustomerDocument> myDocuments() {
        return documents.forCustomer(me().getCustomerId());
    }

    @PostMapping(value = "/api/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('BOOKING_OWN')")
    public ResponseEntity<CustomerDocument> upload(@RequestParam("type") CustomerDocument.Type type,
                                                   @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documents.upload(me(), type, file));
    }

    @GetMapping("/api/me/documents/{id}/content")
    @PreAuthorize("hasAuthority('BOOKING_OWN')")
    public ResponseEntity<InputStreamResource> myContent(@PathVariable String id) {
        return stream(own(id));
    }

    @DeleteMapping("/api/me/documents/{id}")
    @PreAuthorize("hasAuthority('BOOKING_OWN')")
    public ResponseEntity<Void> deleteMine(@PathVariable String id) {
        CustomerDocument doc = own(id);
        if (doc.getStatus() == CustomerDocument.Status.VERIFIED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verified documents can only be removed by the VRMS team");
        }
        documents.delete(doc);
        return ResponseEntity.noContent().build();
    }

    // --- Staff: review documents --------------------------------------------------------------------

    @GetMapping("/api/customers/{customerId}/documents")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public List<CustomerDocument> forCustomer(@PathVariable UUID customerId) {
        customers.getById(customerId);
        return documents.forCustomer(customerId);
    }

    @GetMapping("/api/documents/{id}/content")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<InputStreamResource> content(@PathVariable String id) {
        return stream(documents.get(id));
    }

    @PatchMapping("/api/documents/{id}/review")
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    public CustomerDocument review(@PathVariable String id, @Valid @RequestBody ReviewRequest request) {
        return documents.review(id, request.status(), request.note());
    }

    private ResponseEntity<InputStreamResource> stream(CustomerDocument doc) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .contentLength(doc.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(doc.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(documents.content(doc));
    }

    private Customer me() {
        User user = CurrentUser.get().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue"));
        return customerRepository.findByUser(user)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Complete your profile first"));
    }

    /** A customer's own document; anyone else's looks like it doesn't exist. */
    private CustomerDocument own(String id) {
        CustomerDocument doc = documents.get(id);
        if (!doc.getCustomerId().equals(me().getCustomerId())) {
            throw ApiException.notFound("Document");
        }
        return doc;
    }
}
