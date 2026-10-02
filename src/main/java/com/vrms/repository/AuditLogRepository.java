package com.vrms.repository;

import com.vrms.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /** Dashboard "Recent activity": only events tied to a customer and vehicle. */
    List<AuditLog> findByPlateNumberIsNotNullOrderByTimestampDesc(Pageable pageable);
}
