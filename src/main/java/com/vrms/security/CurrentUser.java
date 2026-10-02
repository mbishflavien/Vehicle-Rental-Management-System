package com.vrms.security;

import com.vrms.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<User> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** Name used as the "actor" in audit logs. */
    public static String displayName() {
        return get().map(User::getFullName).orElse("System");
    }
}
