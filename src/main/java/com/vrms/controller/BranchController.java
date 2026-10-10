package com.vrms.controller;

import com.vrms.model.Branch;
import com.vrms.service.BranchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Pickup branches. Listing is public (booking forms); changes are staff-only (see SecurityConfig). */
@RestController
@Tag(name = "Branches", description = "Pickup branches")
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @GetMapping
    public List<Branch> getAll() { return service.getAll(); }

    @PostMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<Branch> create(@Valid @RequestBody Branch branch) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(branch));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public Branch update(@PathVariable UUID id, @Valid @RequestBody Branch branch) {
        return service.update(id, branch);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
