package com.vrms.service;

import com.vrms.dto.StaffRequest;
import com.vrms.dto.UserView;
import com.vrms.exception.ApiException;
import com.vrms.model.Role;
import com.vrms.model.User;
import com.vrms.repository.UserRepository;
import com.vrms.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Staff accounts (admins and agents), managed by admins. Customers register themselves. */
@Service
public class StaffService {

    private static final EnumSet<Role> STAFF = EnumSet.of(Role.ADMIN, Role.AGENT);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public StaffService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    public List<UserView> getAll() {
        return userRepository.findByRoleInOrderByCreatedAtAsc(STAFF).stream().map(u -> UserView.of(u, null)).toList();
    }

    @Transactional
    public UserView create(StaffRequest req) {
        requireStaffRole(req.role());
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists", "email");
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Set a temporary password for the new account", "password");
        }
        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setJobTitle(blankToNull(req.jobTitle()));
        user.setRole(req.role());
        user.setEnabled(req.enabled() == null || req.enabled());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        User saved = userRepository.save(user);
        audit.log("Staff account created", saved.getFullName() + " added as " + saved.getRole().name().toLowerCase());
        return UserView.of(saved, null);
    }

    @Transactional
    public UserView update(UUID id, StaffRequest req) {
        requireStaffRole(req.role());
        User user = userRepository.findById(id)
                .filter(u -> STAFF.contains(u.getRole()))
                .orElseThrow(() -> ApiException.notFound("Staff member"));
        boolean enabled = req.enabled() == null ? user.isEnabled() : req.enabled();
        boolean self = CurrentUser.get().map(me -> me.getUserId().equals(id)).orElse(false);

        if (self && (!enabled || req.role() != user.getRole())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can't disable your own account or change your own role", "role");
        }
        boolean losesAdmin = user.getRole() == Role.ADMIN && user.isEnabled() && (req.role() != Role.ADMIN || !enabled);
        if (losesAdmin && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VRMS needs at least one active admin", "role");
        }

        user.setFullName(req.fullName().trim());
        user.setJobTitle(blankToNull(req.jobTitle()));
        user.setRole(req.role());
        user.setEnabled(enabled);
        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        User saved = userRepository.save(user);
        audit.log("Staff account updated", saved.getFullName() + " is " + (saved.isEnabled() ? "an active " : "a disabled ")
                + saved.getRole().name().toLowerCase());
        return UserView.of(saved, null);
    }

    private static void requireStaffRole(Role role) {
        if (!STAFF.contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Staff accounts must be Admin or Agent", "role");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
