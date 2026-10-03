package com.vrms.dto;

import com.vrms.model.AuthProvider;
import com.vrms.model.Permission;
import com.vrms.model.Role;
import com.vrms.model.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A user as the frontend sees it (never includes the password hash). The permissions let the UI
 * hide actions the user can't perform; the API enforces them regardless.
 */
public record UserView(UUID userId, String fullName, String email, Role role, String jobTitle, UUID customerId,
                       Set<Permission> permissions, boolean enabled, AuthProvider authProvider,
                       Instant lastLoginAt, Instant createdAt) {
    public static UserView of(User user, UUID customerId) {
        return new UserView(user.getUserId(), user.getFullName(), user.getEmail(), user.getRole(),
                user.getJobTitle(), customerId, user.getRole().getPermissions(), user.isEnabled(),
                user.getAuthProvider(), user.getLastLoginAt(), user.getCreatedAt());
    }
}
