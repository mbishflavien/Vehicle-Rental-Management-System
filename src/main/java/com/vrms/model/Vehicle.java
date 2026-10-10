package com.vrms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicles", indexes = {
        @Index(name = "idx_vehicles_status", columnList = "vehicle_status"),
        @Index(name = "idx_vehicles_branch", columnList = "branch_id")})
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "vehicle_id", updatable = false, nullable = false)
    private UUID vehicleId;

    /** Stored without spaces, e.g. RAB123A. Input like "rab 123 a" is normalized by the setter. */
    @NotBlank(message = "Plate number is required")
    @Pattern(regexp = "RA[A-Z][0-9]{3}[A-Z]", message = "Plate number must follow Rwandan format e.g. RAB 123 A")
    @Column(unique = true, nullable = false)
    private String plateNumber;

    @NotBlank(message = "Model is required")
    @Size(max = 80)
    private String model;

    @NotNull(message = "Daily rate is required")
    @Positive(message = "Daily rate must be greater than zero")
    private Double dailyRate;

    @Enumerated(EnumType.STRING)
    private VehicleStatus vehicleStatus = VehicleStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    private VehicleCategory category;

    @Enumerated(EnumType.STRING)
    private Transmission transmission;

    @Enumerated(EnumType.STRING)
    private FuelType fuelType;

    @Min(value = 1, message = "Seats must be at least 1")
    @Max(value = 60, message = "Seats must be at most 60")
    private Integer seats;

    @Size(max = 1000)
    /** Absolute URL, or a photo served by VRMS itself such as /vehicles/toyota-rav4.jpg. */
    @Pattern(regexp = "^$|https?://.+|/[A-Za-z0-9._/-]+", message = "Image URL must start with http://, https:// or /")
    private String imageUrl;

    /** Home branch where the vehicle is kept and normally picked up. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** Branch chosen in the add/edit form; resolved to {@link #branch} by VehicleService. */
    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private UUID branchId;

    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Vehicle() {}

    public static String normalizePlate(String plate) {
        return plate == null ? null : plate.replaceAll("\\s+", "").toUpperCase();
    }

    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = normalizePlate(plateNumber); }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model == null ? null : model.trim(); }
    public Double getDailyRate() { return dailyRate; }
    public void setDailyRate(Double dailyRate) { this.dailyRate = dailyRate; }
    public VehicleStatus getVehicleStatus() { return vehicleStatus; }
    public void setVehicleStatus(VehicleStatus vehicleStatus) { this.vehicleStatus = vehicleStatus; }
    public VehicleCategory getCategory() { return category; }
    public void setCategory(VehicleCategory category) { this.category = category; }
    public Transmission getTransmission() { return transmission; }
    public void setTransmission(Transmission transmission) { this.transmission = transmission; }
    public FuelType getFuelType() { return fuelType; }
    public void setFuelType(FuelType fuelType) { this.fuelType = fuelType; }
    public Integer getSeats() { return seats; }
    public void setSeats(Integer seats) { this.seats = seats; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl == null ? null : imageUrl.trim(); }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public Instant getCreatedAt() { return createdAt; }
}
