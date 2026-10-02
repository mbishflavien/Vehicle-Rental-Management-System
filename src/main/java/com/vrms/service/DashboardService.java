package com.vrms.service;

import com.vrms.dto.DashboardStats;
import com.vrms.model.ContractStatus;
import com.vrms.model.VehicleStatus;
import com.vrms.repository.CustomerRepository;
import com.vrms.repository.RentalContractRepository;
import com.vrms.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class DashboardService {

    private static final Duration PERIOD = Duration.ofDays(30);

    private final VehicleRepository vehicleRepository;
    private final RentalContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final AuditService audit;

    public DashboardService(VehicleRepository vehicleRepository, RentalContractRepository contractRepository,
                            CustomerRepository customerRepository, AuditService audit) {
        this.vehicleRepository = vehicleRepository;
        this.contractRepository = contractRepository;
        this.customerRepository = customerRepository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        Instant now = Instant.now();
        Instant periodStart = now.minus(PERIOD);
        Instant previousStart = periodStart.minus(PERIOD);

        double revenue = contractRepository.sumRevenueBetween(periodStart, now.plusSeconds(1));
        double previousRevenue = contractRepository.sumRevenueBetween(previousStart, periodStart);
        Double revenueTrend = previousRevenue > 0 ? (revenue - previousRevenue) / previousRevenue * 100 : null;

        long total = vehicleRepository.count();
        long rented = vehicleRepository.countByVehicleStatus(VehicleStatus.RENTED);
        long available = vehicleRepository.countByVehicleStatus(VehicleStatus.AVAILABLE);
        long maintenance = vehicleRepository.countByVehicleStatus(VehicleStatus.MAINTENANCE);
        double utilization = total == 0 ? 0 : (double) rented / total * 100;

        long newContracts = contractRepository.countByContractStatusAndCreatedAtBetween(ContractStatus.ACTIVE, periodStart, now)
                + contractRepository.countByContractStatusAndCreatedAtBetween(ContractStatus.COMPLETED, periodStart, now);

        return new DashboardStats(
                revenue,
                revenueTrend,
                contractRepository.countByContractStatus(ContractStatus.ACTIVE),
                newContracts,
                utilization,
                rented,
                available,
                total,
                maintenance,
                contractRepository.countByContractStatus(ContractStatus.PENDING),
                customerRepository.count(),
                audit.recentContractActivity(6));
    }
}
