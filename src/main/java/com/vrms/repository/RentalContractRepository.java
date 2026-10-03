package com.vrms.repository;

import com.vrms.model.Branch;
import com.vrms.model.ContractStatus;
import com.vrms.model.Customer;
import com.vrms.model.RentalContract;
import com.vrms.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RentalContractRepository extends JpaRepository<RentalContract, UUID> {
    List<RentalContract> findAllByOrderByCreatedAtDesc();
    List<RentalContract> findByCustomerOrderByCreatedAtDesc(Customer customer);
    List<RentalContract> findByVehicle(Vehicle vehicle);
    List<RentalContract> findByCustomer(Customer customer);
    long countByContractStatus(ContractStatus status);
    boolean existsByPickupBranch(Branch branch);
    boolean existsByVehicleAndContractStatusIn(Vehicle vehicle, Collection<ContractStatus> statuses);
    boolean existsByCustomerAndContractStatusIn(Customer customer, Collection<ContractStatus> statuses);

    /** Revenue from contracts that went ahead (active or completed), created in [from, to). */
    @Query("select coalesce(sum(c.totalCost), 0) from RentalContract c " +
           "where c.contractStatus in (com.vrms.model.ContractStatus.ACTIVE, com.vrms.model.ContractStatus.COMPLETED) " +
           "and c.createdAt >= :from and c.createdAt < :to")
    double sumRevenueBetween(@Param("from") Instant from, @Param("to") Instant to);

    long countByContractStatusAndCreatedAtBetween(ContractStatus status, Instant from, Instant to);
}
