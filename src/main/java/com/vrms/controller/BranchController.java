package com.vrms.controller;

import com.vrms.model.Branch;
import com.vrms.service.BranchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Pickup branches. Listing is public (booking forms); changes are staff-only (see SecurityConfig). */
@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @GetMapping
    public List<Branch> getAll() { return service.getAll(); }

    @PostMapping
    public ResponseEntity<Branch> create(@Valid @RequestBody Branch branch) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(branch));
    }

    @PutMapping("/{id}")
    public Branch update(@PathVariable UUID id, @Valid @RequestBody Branch branch) {
        return service.update(id, branch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
