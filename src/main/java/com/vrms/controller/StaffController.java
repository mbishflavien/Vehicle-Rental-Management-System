package com.vrms.controller;

import com.vrms.dto.StaffRequest;
import com.vrms.dto.UserView;
import com.vrms.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasAuthority('STAFF_MANAGE')")
public class StaffController {

    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserView> getAll() { return service.getAll(); }

    @PostMapping
    public ResponseEntity<UserView> create(@Valid @RequestBody StaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public UserView update(@PathVariable UUID id, @Valid @RequestBody StaffRequest request) {
        return service.update(id, request);
    }
}
