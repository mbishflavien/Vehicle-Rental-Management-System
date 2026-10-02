package com.vrms.controller;

import com.vrms.dto.ContractRequest;
import com.vrms.dto.StatusUpdateRequest;
import com.vrms.model.RentalContract;
import com.vrms.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contracts")
public class RentalContractController {

    private final ContractService service;

    public RentalContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    public List<RentalContract> getAllContracts() { return service.getAll(); }

    @GetMapping("/{id}")
    public RentalContract getContract(@PathVariable UUID id) { return service.getById(id); }

    @PostMapping
    public ResponseEntity<RentalContract> createContract(@Valid @RequestBody ContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issue(request));
    }

    /** Approve (ACTIVE), return (COMPLETED) or cancel (CANCELLED). */
    @PatchMapping("/{id}/status")
    public RentalContract updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContract(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
