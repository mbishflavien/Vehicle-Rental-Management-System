package com.vrms.service;

import com.vrms.dto.BookingRequest;
import com.vrms.dto.ContractRequest;
import com.vrms.exception.ApiException;
import com.vrms.messaging.RentalEvent;
import com.vrms.messaging.RentalEventPublisher;
import com.vrms.model.*;
import com.vrms.repository.CustomerRepository;
import com.vrms.repository.RentalContractRepository;
import com.vrms.repository.VehicleRepository;
import com.vrms.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rental contract lifecycle. Vehicle status always follows the contract:
 *
 *   customer books online  -> PENDING   (vehicle RESERVED)
 *   staff issues directly  -> ACTIVE    (vehicle RENTED)
 *   PENDING -> ACTIVE      approve / hand over keys (vehicle RENTED)
 *   ACTIVE  -> COMPLETED   vehicle returned         (vehicle AVAILABLE)
 *   PENDING/ACTIVE -> CANCELLED                     (vehicle AVAILABLE)
 */
@Service
public class ContractService {

    static final int MAX_RENTAL_DAYS = 90;

    private static final Map<ContractStatus, Set<ContractStatus>> ALLOWED = Map.of(
            ContractStatus.PENDING, Set.of(ContractStatus.ACTIVE, ContractStatus.CANCELLED),
            ContractStatus.ACTIVE, Set.of(ContractStatus.COMPLETED, ContractStatus.CANCELLED),
            ContractStatus.COMPLETED, Set.of(),
            ContractStatus.CANCELLED, Set.of());

    private final RentalContractRepository contractRepository;
    private final VehicleRepository vehicleRepository;
    private final CustomerRepository customerRepository;
    private final BranchService branchService;
    private final AuditService audit;
    private final RentalEventPublisher events;

    public ContractService(RentalContractRepository contractRepository, VehicleRepository vehicleRepository,
                           CustomerRepository customerRepository, BranchService branchService, AuditService audit,
                           RentalEventPublisher events) {
        this.contractRepository = contractRepository;
        this.vehicleRepository = vehicleRepository;
        this.customerRepository = customerRepository;
        this.branchService = branchService;
        this.audit = audit;
        this.events = events;
    }

    public List<RentalContract> getAll() {
        return contractRepository.findAllByOrderByCreatedAtDesc();
    }

    public RentalContract getById(UUID id) {
        return contractRepository.findById(id).orElseThrow(() -> ApiException.notFound("Contract"));
    }

    public List<RentalContract> getForCustomer(Customer customer) {
        return contractRepository.findByCustomerOrderByCreatedAtDesc(customer);
    }

    /** Staff "Issue new contract": the vehicle is handed over now, so the contract starts ACTIVE. */
    @Transactional
    public RentalContract issue(ContractRequest req) {
        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> ApiException.notFound("Customer"));
        RentalContract contract = open(customer, req.vehicleId(), req.startDate(), req.endDate(),
                req.pickupBranchId(), ContractStatus.ACTIVE, false);
        CurrentUser.get().ifPresent(contract::setIssuedBy);
        audit.logContract("New contract", contract,
                "Contract issued to " + customer.getFullName() + " for " + contract.getVehicle().getPlateNumber());
        audit.log("Vehicle status", contract.getVehicle().getPlateNumber() + " changed to Rented");
        events.contract(RentalEvent.Type.CONTRACT_ISSUED, contract);
        return contract;
    }

    /** Customer "Reserve & book": held as PENDING until staff approve it. */
    @Transactional
    public RentalContract book(Customer customer, BookingRequest req) {
        RentalContract contract = open(customer, req.vehicleId(), req.startDate(), req.endDate(),
                req.pickupBranchId(), ContractStatus.PENDING, true);
        audit.logContract("Booking request", contract,
                customer.getFullName() + " requested " + contract.getVehicle().getPlateNumber() + " online");
        events.contract(RentalEvent.Type.BOOKING_REQUESTED, contract);
        return contract;
    }

    @Transactional
    public RentalContract changeStatus(UUID id, ContractStatus target) {
        RentalContract contract = getById(id);
        ContractStatus current = contract.getContractStatus();
        if (!ALLOWED.get(current).contains(target)) {
            throw ApiException.badRequest("A " + VehicleService.pretty(current).toLowerCase()
                    + " contract can't be changed to " + VehicleService.pretty(target).toLowerCase());
        }

        Vehicle vehicle = vehicleRepository.findByIdForUpdate(contract.getVehicle().getVehicleId())
                .orElseThrow(() -> ApiException.notFound("Vehicle"));
        VehicleStatus vehicleStatus = target == ContractStatus.ACTIVE ? VehicleStatus.RENTED : VehicleStatus.AVAILABLE;
        vehicle.setVehicleStatus(vehicleStatus);
        vehicleRepository.save(vehicle);

        contract.setContractStatus(target);
        if (target == ContractStatus.ACTIVE && contract.getIssuedBy() == null) {
            CurrentUser.get().ifPresent(contract::setIssuedBy);
        }
        RentalContract saved = contractRepository.save(contract);

        String event = switch (target) {
            case ACTIVE -> "Contract approved";
            case COMPLETED -> "Returned";
            case CANCELLED -> "Cancelled";
            default -> "Contract updated";
        };
        audit.logContract(event, saved, "Contract for " + saved.getCustomer().getFullName() + " marked "
                + VehicleService.pretty(target).toLowerCase());
        audit.log("Vehicle status", vehicle.getPlateNumber() + " changed to " + VehicleService.pretty(vehicleStatus));
        events.statusChanged(saved, target);
        return saved;
    }

    /** A customer may cancel their own booking while it is still pending. */
    @Transactional
    public RentalContract cancelOwnBooking(Customer customer, UUID id) {
        RentalContract contract = getById(id);
        if (!contract.getCustomer().getCustomerId().equals(customer.getCustomerId())) {
            throw ApiException.notFound("Booking");
        }
        if (contract.getContractStatus() != ContractStatus.PENDING) {
            throw ApiException.badRequest("Only pending bookings can be cancelled online. Please contact the VRMS team.");
        }
        return changeStatus(id, ContractStatus.CANCELLED);
    }

    /** Deleting an open contract releases its vehicle first. */
    @Transactional
    public void delete(UUID id) {
        RentalContract contract = getById(id);
        if (VehicleService.OPEN.contains(contract.getContractStatus())) {
            Vehicle vehicle = contract.getVehicle();
            vehicle.setVehicleStatus(VehicleStatus.AVAILABLE);
            vehicleRepository.save(vehicle);
        }
        contractRepository.delete(contract);
        audit.log("Contract deleted", "Contract for " + contract.getCustomer().getFullName() + " ("
                + contract.getVehicle().getPlateNumber() + ") deleted");
    }

    public static long rentalDays(LocalDate start, LocalDate end) {
        return Math.max(1, ChronoUnit.DAYS.between(start, end));
    }

    private RentalContract open(Customer customer, UUID vehicleId, LocalDate start, LocalDate end,
                                UUID pickupBranchId, ContractStatus status, boolean onlineBooking) {
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Return date must be after the start date", "endDate");
        }
        if (onlineBooking && start.isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Start date can't be in the past", "startDate");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RENTAL_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Rentals are limited to " + MAX_RENTAL_DAYS + " days", "endDate");
        }

        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> ApiException.notFound("Vehicle"));
        if (vehicle.getVehicleStatus() != VehicleStatus.AVAILABLE) {
            throw new ApiException(HttpStatus.CONFLICT,
                    vehicle.getModel() + " is not available right now (" + VehicleService.pretty(vehicle.getVehicleStatus()).toLowerCase() + ")",
                    "vehicleId");
        }

        RentalContract contract = new RentalContract();
        contract.setCustomer(customer);
        contract.setVehicle(vehicle);
        contract.setStartDate(start);
        contract.setEndDate(end);
        contract.setPickupBranch(pickupBranchId == null ? vehicle.getBranch() : branchService.getById(pickupBranchId));
        contract.setTotalCost(rentalDays(start, end) * vehicle.getDailyRate());
        contract.setContractStatus(status);

        vehicle.setVehicleStatus(status == ContractStatus.ACTIVE ? VehicleStatus.RENTED : VehicleStatus.RESERVED);
        vehicleRepository.save(vehicle);
        return contractRepository.save(contract);
    }
}
