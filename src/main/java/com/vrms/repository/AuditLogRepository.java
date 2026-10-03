package com.vrms.repository;

import com.vrms.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /** Dashboard "Recent activity": only events tied to a customer and vehicle. */
    List<AuditLog> findByPlateNumberIsNotNullOrderByTimestampDesc(Pageable pageable);
}
