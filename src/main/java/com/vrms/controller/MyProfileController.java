package com.vrms.controller;

import com.vrms.dto.ProfileRequest;
import com.vrms.exception.ApiException;
import com.vrms.model.Customer;
import com.vrms.model.User;
import com.vrms.repository.CustomerRepository;
import com.vrms.security.CurrentUser;
import com.vrms.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** The signed-in customer's own profile (phone and driver license). */
@RestController
@RequestMapping("/api/me/profile")
@PreAuthorize("hasAuthority('BOOKING_OWN')")
public class MyProfileController {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    public MyProfileController(CustomerService customerService, CustomerRepository customerRepository) {
        this.customerService = customerService;
        this.customerRepository = customerRepository;
    }

    @GetMapping
    public Customer get() {
        return customerRepository.findByUser(current())
                .orElseThrow(() -> ApiException.notFound("Customer profile"));
    }

    /** Creates the profile, or updates phone and license on an existing one. */
    @PutMapping
    public Customer save(@Valid @RequestBody ProfileRequest request) {
        return customerService.saveOwnProfile(current(), request);
    }

    private static User current() {
        return CurrentUser.get().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue"));
    }
}
