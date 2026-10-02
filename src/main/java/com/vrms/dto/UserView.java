package com.vrms.dto;

import com.vrms.model.Role;
import com.vrms.model.User;

import java.util.UUID;

/** The signed-in user as the frontend sees it (never includes the password hash). */
public record UserView(UUID userId, String fullName, String email, Role role, String jobTitle, UUID customerId) {
    public static UserView of(User user, UUID customerId) {
        return new UserView(user.getUserId(), user.getFullName(), user.getEmail(), user.getRole(),
                user.getJobTitle(), customerId);
    }
}
