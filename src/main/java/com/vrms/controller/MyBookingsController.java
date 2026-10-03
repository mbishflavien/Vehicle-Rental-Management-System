package com.vrms.controller;

import com.vrms.dto.BookingRequest;
import com.vrms.exception.ApiException;
import com.vrms.model.Customer;
import com.vrms.model.RentalContract;
import com.vrms.repository.CustomerRepository;
import com.vrms.security.CurrentUser;
import com.vrms.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** The signed-in customer's own bookings. */
@RestController
@RequestMapping("/api/me/bookings")
@PreAuthorize("hasAuthority('BOOKING_OWN')")
public class MyBookingsController {

    private final ContractService contractService;
    private final CustomerRepository customerRepository;

    public MyBookingsController(ContractService contractService, CustomerRepository customerRepository) {
        this.contractService = contractService;
        this.customerRepository = customerRepository;
    }

    @GetMapping
    public List<RentalContract> myBookings() {
        return contractService.getForCustomer(currentCustomer());
    }

    @PostMapping
    public ResponseEntity<RentalContract> book(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractService.book(currentCustomer(), request));
    }

    @PostMapping("/{id}/cancel")
    public RentalContract cancel(@PathVariable UUID id) {
        return contractService.cancelOwnBooking(currentCustomer(), id);
    }

    private Customer currentCustomer() {
        return CurrentUser.get()
                .flatMap(customerRepository::findByUser)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "No customer profile is linked to this account"));
    }
}
