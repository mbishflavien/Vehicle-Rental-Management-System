package com.vrms.controller;

import com.vrms.model.RentalContract;
import com.vrms.service.VRMSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contracts")
public class RentalContractController {

    @Autowired private VRMSService service;

    @GetMapping
    public List<RentalContract> getAllContracts() { return service.getAllContracts(); }

    @GetMapping("/{id}")
    public ResponseEntity<RentalContract> getContract(@PathVariable UUID id) {
        RentalContract c = service.getContractById(id);
        return c != null ? ResponseEntity.ok(c) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<RentalContract> createContract(@RequestBody RentalContract contract) {
        return ResponseEntity.ok(service.createContract(contract));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContract(@PathVariable UUID id) {
        service.deleteContract(id);
        return ResponseEntity.noContent().build();
    }
}