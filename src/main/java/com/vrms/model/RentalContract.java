package com.vrms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rental_contracts", indexes = {
        @Index(name = "idx_contracts_status", columnList = "contract_status"),
        @Index(name = "idx_contracts_created_at", columnList = "created_at"),
        @Index(name = "idx_contracts_customer", columnList = "customer_id"),
        @Index(name = "idx_contracts_vehicle", columnList = "vehicle_id")})
public class RentalContract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "contract_id", updatable = false, nullable = false)
    private UUID contractId;

    @NotNull
    private LocalDate startDate;

    /** Return date. Rental days = endDate - startDate. */
    @NotNull
    private LocalDate endDate;

    private Double totalCost;

    @Enumerated(EnumType.STRING)
    private ContractStatus contractStatus = ContractStatus.PENDING;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pickup_branch_id")
    private Branch pickupBranch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    /** Staff member who issued or approved the contract. Null while an online booking is pending. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "issued_by")
    private User issuedBy;

    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public RentalContract() {}

    public UUID getContractId() { return contractId; }
    public void setContractId(UUID contractId) { this.contractId = contractId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }
    public ContractStatus getContractStatus() { return contractStatus; }
    public void setContractStatus(ContractStatus contractStatus) { this.contractStatus = contractStatus; }
    public Branch getPickupBranch() { return pickupBranch; }
    public void setPickupBranch(Branch pickupBranch) { this.pickupBranch = pickupBranch; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    public User getIssuedBy() { return issuedBy; }
    public void setIssuedBy(User issuedBy) { this.issuedBy = issuedBy; }
    public Instant getCreatedAt() { return createdAt; }

    @JsonProperty(value = "issuedByName", access = JsonProperty.Access.READ_ONLY)
    public String issuedByName() { return issuedBy == null ? null : issuedBy.getFullName(); }
}
