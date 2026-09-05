package com.vrms.model;

import javax.persistence.*;
import javax.validation.constraints.*;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vehicleId;

    @NotNull(message = "Plate number is required")
    @Size(min = 5, max = 10, message = "Plate number must be between 5 and 10 characters")
    @Column(name = "plate_number", nullable = false, unique = true)
    private String plateNumber;

    @NotNull(message = "Vehicle model is required")
    @Column(name = "model", nullable = false)
    private String model;

    @NotNull(message = "Daily rate is required")
    @Min(value = 10, message = "Minimum daily rate is 10")
    @Column(name = "daily_rate", nullable = false)
    private Double dailyRate;

    @Column(name = "status")
    private String status = "AVAILABLE";

    public Vehicle() {}

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getDailyRate() { return dailyRate; }
    public void setDailyRate(Double dailyRate) { this.dailyRate = dailyRate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}