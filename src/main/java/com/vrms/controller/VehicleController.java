package com.vrms.controller;

import com.vrms.model.Vehicle;
import com.vrms.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** GET is public (fleet browsing); changes are staff-only (see SecurityConfig). */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    @GetMapping
    public List<Vehicle> getAllVehicles() { return service.getAll(); }

    @GetMapping("/{id}")
    public Vehicle getVehicle(@PathVariable UUID id) { return service.getById(id); }

    @PostMapping
    public ResponseEntity<Vehicle> createVehicle(@Valid @RequestBody Vehicle vehicle) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(vehicle));
    }

    @PutMapping("/{id}")
    public Vehicle updateVehicle(@PathVariable UUID id, @Valid @RequestBody Vehicle vehicle) {
        return service.update(id, vehicle);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
