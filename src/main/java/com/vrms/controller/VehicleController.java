package com.vrms.controller;

import com.vrms.model.Vehicle;
import com.vrms.service.VRMSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    @Autowired private VRMSService service;

    @GetMapping
    public List<Vehicle> getAllVehicles() { return service.getAllVehicles(); }

    @GetMapping("/{id}")
    public ResponseEntity<Vehicle> getVehicle(@PathVariable UUID id) {
        Vehicle v = service.getVehicleById(id);
        return v != null ? ResponseEntity.ok(v) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<Vehicle> createVehicle(@Valid @RequestBody Vehicle vehicle) {
        return ResponseEntity.ok(service.saveVehicle(vehicle));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Vehicle> updateVehicle(@PathVariable UUID id, @Valid @RequestBody Vehicle vehicle) {
        vehicle.setVehicleId(id);
        return ResponseEntity.ok(service.saveVehicle(vehicle));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable UUID id) {
        service.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }
}