package com.vrms.service;

import com.vrms.config.CacheConfig;
import com.vrms.exception.ApiException;
import com.vrms.model.ContractStatus;
import com.vrms.model.Vehicle;
import com.vrms.model.VehicleStatus;
import com.vrms.repository.RentalContractRepository;
import com.vrms.repository.VehicleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class VehicleService {

    static final EnumSet<ContractStatus> OPEN = EnumSet.of(ContractStatus.PENDING, ContractStatus.ACTIVE);

    private final VehicleRepository vehicleRepository;
    private final RentalContractRepository contractRepository;
    private final BranchService branchService;
    private final AuditService audit;

    public VehicleService(VehicleRepository vehicleRepository, RentalContractRepository contractRepository,
                          BranchService branchService, AuditService audit) {
        this.vehicleRepository = vehicleRepository;
        this.contractRepository = contractRepository;
        this.branchService = branchService;
        this.audit = audit;
    }

    @Cacheable(CacheConfig.FLEET)
    public List<Vehicle> getAll() {
        return List.copyOf(vehicleRepository.findAllBy(Sort.by("model", "plateNumber")));
    }

    public Vehicle getById(UUID id) {
        return vehicleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Vehicle"));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.FLEET, CacheConfig.DASHBOARD}, allEntries = true)
    public Vehicle create(Vehicle vehicle) {
        vehicle.setVehicleId(null);
        if (vehicleRepository.existsByPlateNumber(vehicle.getPlateNumber())) {
            throw ApiException.conflict("A vehicle with this plate number is already registered", "plateNumber");
        }
        // Rented/Reserved are set only by contracts; new vehicles start Available or in Maintenance.
        if (vehicle.getVehicleStatus() != VehicleStatus.MAINTENANCE) {
            vehicle.setVehicleStatus(VehicleStatus.AVAILABLE);
        }
        vehicle.setBranch(branchService.resolve(vehicle.getBranchId()));
        Vehicle saved = vehicleRepository.save(vehicle);
        audit.log("Vehicle added", saved.getModel() + " (" + saved.getPlateNumber() + ") added to the fleet");
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.FLEET, CacheConfig.DASHBOARD}, allEntries = true)
    public Vehicle update(UUID id, Vehicle changes) {
        Vehicle existing = getById(id);
        if (vehicleRepository.existsByPlateNumberAndVehicleIdNot(changes.getPlateNumber(), id)) {
            throw ApiException.conflict("A vehicle with this plate number is already registered", "plateNumber");
        }

        VehicleStatus before = existing.getVehicleStatus();
        VehicleStatus requested = changes.getVehicleStatus() == null ? before : changes.getVehicleStatus();
        if (requested != before) {
            boolean busy = before == VehicleStatus.RENTED || before == VehicleStatus.RESERVED;
            boolean manual = requested == VehicleStatus.AVAILABLE || requested == VehicleStatus.MAINTENANCE;
            if (busy || !manual) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Rented and reserved status is managed by rental contracts. Complete or cancel the contract instead.",
                        "vehicleStatus");
            }
        }

        existing.setPlateNumber(changes.getPlateNumber());
        existing.setModel(changes.getModel());
        existing.setDailyRate(changes.getDailyRate());
        existing.setCategory(changes.getCategory());
        existing.setTransmission(changes.getTransmission());
        existing.setFuelType(changes.getFuelType());
        existing.setSeats(changes.getSeats());
        existing.setImageUrl(changes.getImageUrl());
        existing.setBranch(branchService.resolve(changes.getBranchId()));
        existing.setVehicleStatus(requested);
        Vehicle saved = vehicleRepository.save(existing);

        audit.log("Vehicle updated", saved.getPlateNumber() + " details updated");
        if (requested != before) {
            audit.log("Vehicle status", saved.getPlateNumber() + " changed to " + pretty(requested));
        }
        return saved;
    }

    /**
     * Deletes a vehicle (BR-06). Refused while a booking or rental is open; otherwise its closed
     * contract history is removed with it.
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.FLEET, CacheConfig.DASHBOARD}, allEntries = true)
    public void delete(UUID id) {
        Vehicle vehicle = getById(id);
        if (contractRepository.existsByVehicleAndContractStatusIn(vehicle, OPEN)) {
            throw ApiException.conflict("This vehicle has an open booking or rental. Complete or cancel it first.", null);
        }
        contractRepository.deleteAll(contractRepository.findByVehicle(vehicle));
        vehicleRepository.delete(vehicle);
        audit.log("Vehicle removed", vehicle.getModel() + " (" + vehicle.getPlateNumber() + ") removed from the fleet");
    }

    static String pretty(Enum<?> e) {
        String s = e.name().toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
