package com.vrms.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.vrms.model.Permission.*;

/** Role-based access control: each role grants a fixed set of permissions. */
public enum Role {
    /** Operations manager: everything, including deletes, audit logs and staff accounts. */
    ADMIN(EnumSet.complementOf(EnumSet.of(BOOKING_OWN))),

    /** Rental agent at a branch desk: day-to-day rentals, but no deletes, audit log or staff management. */
    AGENT(EnumSet.of(VEHICLE_WRITE, CUSTOMER_READ, CUSTOMER_WRITE, CONTRACT_READ, CONTRACT_WRITE,
            DASHBOARD_READ, NOTIFICATION_READ, DOCUMENT_READ)),

    /** Self-registered customer who can browse the fleet and book vehicles. */
    CUSTOMER(EnumSet.of(BOOKING_OWN));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean isStaff() {
        return this != CUSTOMER;
    }

    /** ROLE_X plus one authority per permission, as Spring Security sees them. */
    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_" + name()));
        permissions.forEach(p -> list.add(new SimpleGrantedAuthority(p.name())));
        return list;
    }
}
