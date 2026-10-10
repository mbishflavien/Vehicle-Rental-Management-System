package com.vrms.repository;

import com.vrms.model.Branch;
import com.vrms.model.Vehicle;
import com.vrms.model.VehicleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    /** Locks the vehicle row until the transaction ends, so two bookings can't take the same car at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.vehicleId = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") UUID id);

    /** The whole fleet with each vehicle's branch in one query (no N+1). */
    @EntityGraph(attributePaths = "branch")
    List<Vehicle> findAllBy(Sort sort);

    boolean existsByPlateNumber(String plateNumber);
    boolean existsByPlateNumberAndVehicleIdNot(String plateNumber, UUID vehicleId);
    long countByVehicleStatus(VehicleStatus status);
    boolean existsByBranch(Branch branch);
}
