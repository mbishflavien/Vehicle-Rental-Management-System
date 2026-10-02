package com.vrms.service;

import com.vrms.dto.AuthResponse;
import com.vrms.dto.LoginRequest;
import com.vrms.dto.RegisterRequest;
import com.vrms.dto.UserView;
import com.vrms.exception.ApiException;
import com.vrms.model.Customer;
import com.vrms.model.Role;
import com.vrms.model.User;
import com.vrms.repository.CustomerRepository;
import com.vrms.repository.UserRepository;
import com.vrms.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;

    public AuthService(UserRepository userRepository, CustomerRepository customerRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, AuditService audit) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    /**
     * Creates a CUSTOMER login and its customer profile. If staff already registered this person
     * as a walk-in customer (same email and same driver license), the new login is linked to that
     * existing profile instead of creating a duplicate (BR-01).
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        String license = req.driverLicenseNumber().trim().toUpperCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists. Try signing in.", "email");
        }

        Customer customer = customerRepository.findByEmailIgnoreCase(email).orElse(null);
        if (customer != null && !customer.getDriverLicenseNumber().equals(license)) {
            throw ApiException.conflict("This email is registered to a different driver license. Contact the VRMS team.", "email");
        }
        if (customer == null && customerRepository.existsByDriverLicenseNumber(license)) {
            throw ApiException.conflict("This driver license is already registered", "driverLicenseNumber");
        }

        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.CUSTOMER);
        user = userRepository.save(user);

        if (customer == null) {
            customer = new Customer();
            customer.setEmail(email);
            customer.setDriverLicenseNumber(license);
        }
        customer.setFullName(req.fullName());
        if (req.phoneNumber() != null && !req.phoneNumber().isBlank()) {
            customer.setPhoneNumber(req.phoneNumber());
        }
        customer.setUser(user);
        customer = customerRepository.save(customer);

        audit.log("Account created", user.getFullName(), "Customer registered online with " + email);
        return response(user, customer.getCustomerId());
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        audit.log("User signed in", user.getFullName(), "Successful authentication (" + user.getRole().name().toLowerCase() + ")");
        return response(user, customerIdOf(user));
    }

    public UserView me(User user) {
        return UserView.of(user, customerIdOf(user));
    }

    private UUID customerIdOf(User user) {
        return customerRepository.findByUser(user).map(Customer::getCustomerId).orElse(null);
    }

    private AuthResponse response(User user, UUID customerId) {
        return new AuthResponse(jwtService.issueToken(user), jwtService.lifetimeSeconds(), UserView.of(user, customerId));
    }
}
