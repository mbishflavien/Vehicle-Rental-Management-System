package com.vrms.repository;

import com.vrms.model.CustomerDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerDocumentRepository extends MongoRepository<CustomerDocument, String> {
    List<CustomerDocument> findByCustomerIdOrderByUploadedAtDesc(UUID customerId);
    long countByCustomerId(UUID customerId);
}
