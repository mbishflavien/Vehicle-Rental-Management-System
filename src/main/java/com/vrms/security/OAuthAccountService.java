package com.vrms.security;

import com.vrms.exception.ApiException;
import com.vrms.model.AuthProvider;
import com.vrms.model.Role;
import com.vrms.model.User;
import com.vrms.repository.UserRepository;
import com.vrms.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Maps an identity confirmed by Google or GitHub to a VRMS account. The provider has verified the
 * email address, so an existing account with that email is signed in; otherwise a new CUSTOMER
 * account is created (they add their driver license before booking).
 */
@Service
public class OAuthAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public OAuthAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional
    public User findOrCreate(AuthProvider provider, String email, String fullName) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Your " + label(provider) + " account didn't share a verified email address");
        }
        String normalized = email.trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (user != null) {
            if (!user.isEnabled()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "This account has been disabled. Contact the VRMS team.");
            }
            user.setLastLoginAt(Instant.now());
            audit.log("User signed in", user.getFullName(), "Signed in with " + label(provider));
            return userRepository.save(user);
        }

        user = new User();
        user.setEmail(normalized);
        user.setFullName(fullName == null || fullName.isBlank() ? normalized.substring(0, normalized.indexOf('@')) : fullName.trim());
        user.setRole(Role.CUSTOMER);
        user.setAuthProvider(provider);
        // No usable password: this account signs in through the provider (it can set one later).
        user.setPasswordHash(passwordEncoder.encode(randomSecret()));
        user.setLastLoginAt(Instant.now());
        User saved = userRepository.save(user);
        audit.log("Account created", saved.getFullName(), "Customer registered with " + label(provider) + " (" + normalized + ")");
        return saved;
    }

    private static String label(AuthProvider provider) {
        return provider == AuthProvider.GITHUB ? "GitHub" : "Google";
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
