package com.vrms.model;

import org.hibernate.annotations.GenericGenerator;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "vehicle_id", updatable = false, nullable = false)
    private UUID vehicleId;

    @NotNull
    @Pattern(regexp = "RAB[0-9]{3}[A-Z]", message = "Plate number must follow Rwandan format e.g. RAB123A")
    @Column(unique = true, nullable = false)
    private String plateNumber;

    @NotNull
    private String model;

    @NotNull
    private Double dailyRate;

    @Enumerated(EnumType.STRING)
    private VehicleStatus vehicleStatus = VehicleStatus.AVAILABLE;

    public Vehicle() {}

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Double getDailyRate() { return dailyRate; }
    public void setDailyRate(Double dailyRate) { this.dailyRate = dailyRate; }
    public VehicleStatus getVehicleStatus() { return vehicleStatus; }
    public void setVehicleStatus(VehicleStatus vehicleStatus) { this.vehicleStatus = vehicleStatus; }
}