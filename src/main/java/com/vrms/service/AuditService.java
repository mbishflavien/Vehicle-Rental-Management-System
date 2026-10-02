package com.vrms.service;

import com.vrms.model.AuditLog;
import com.vrms.model.RentalContract;
import com.vrms.repository.AuditLogRepository;
import com.vrms.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/** Writes the System Logs trail. Called from the other services inside their transactions. */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(String event, String details) {
        repository.save(new AuditLog(event, CurrentUser.displayName(), details));
    }

    public void log(String event, String actor, String details) {
        repository.save(new AuditLog(event, actor, details));
    }

    /** Contract events also feed the dashboard's "Recent activity" table. */
    public void logContract(String event, RentalContract contract, String details) {
        AuditLog entry = new AuditLog(event, CurrentUser.displayName(), details);
        entry.setAmount(contract.getTotalCost());
        entry.setPlateNumber(contract.getVehicle().getPlateNumber());
        entry.setCustomerName(contract.getCustomer().getFullName());
        repository.save(entry);
    }

    public List<AuditLog> latest(int limit) {
        return repository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit));
    }

    public List<AuditLog> recentContractActivity(int limit) {
        return repository.findByPlateNumberIsNotNullOrderByTimestampDesc(PageRequest.of(0, limit));
    }
}
