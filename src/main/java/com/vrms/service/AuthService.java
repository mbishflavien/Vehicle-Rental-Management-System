package com.vrms.service;

import com.vrms.dto.AuthResponse;
import com.vrms.dto.LoginRequest;
import com.vrms.dto.PasswordChangeRequest;
import com.vrms.dto.RegisterRequest;
import com.vrms.dto.UserView;
import com.vrms.exception.ApiException;
import com.vrms.model.Customer;
import com.vrms.model.Role;
import com.vrms.model.User;
import com.vrms.repository.CustomerRepository;
import com.vrms.repository.UserRepository;
import com.vrms.security.JwtService;
import com.vrms.security.LoginAttemptService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttempts;
    private final AuditService audit;

    public AuthService(UserRepository userRepository, CustomerRepository customerRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptService loginAttempts, AuditService audit) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttempts = loginAttempts;
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
        user.setLastLoginAt(Instant.now());
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
    public AuthResponse login(LoginRequest req, String clientIp) {
        String email = req.email().trim();
        loginAttempts.checkAllowed(email, clientIp);

        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            loginAttempts.recordFailure(email, clientIp);
            audit.log("Failed sign-in", "System", "Wrong email or password for " + email.toLowerCase() + " from " + clientIp);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account has been disabled. Contact the VRMS team.");
        }
        loginAttempts.recordSuccess(email, clientIp);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        audit.log("User signed in", user.getFullName(), "Successful authentication (" + user.getRole().name().toLowerCase() + ") from " + clientIp);
        return response(user, customerIdOf(user));
    }

    @Transactional
    public void changePassword(User current, PasswordChangeRequest req) {
        User user = userRepository.findById(current.getUserId()).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is not correct", "currentPassword");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        audit.log("Password changed", user.getFullName(), "Password updated by the account owner");
    }

    public UserView me(User user) {
        return UserView.of(user, customerIdOf(user));
    }

    public AuthResponse tokenFor(User user) {
        return response(user, customerIdOf(user));
    }

    private UUID customerIdOf(User user) {
        return customerRepository.findByUser(user).map(Customer::getCustomerId).orElse(null);
    }

    private AuthResponse response(User user, UUID customerId) {
        return new AuthResponse(jwtService.issueToken(user), "Bearer", jwtService.lifetimeSeconds(), UserView.of(user, customerId));
    }
}
