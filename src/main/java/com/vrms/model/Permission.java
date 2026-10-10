package com.vrms.model;

/**
 * Fine-grained permissions checked on every protected endpoint (see the @PreAuthorize annotations
 * on the controllers). Roles are bundles of these; see {@link Role}.
 */
public enum Permission {
    VEHICLE_WRITE,
    VEHICLE_DELETE,
    CUSTOMER_READ,
    CUSTOMER_WRITE,
    CUSTOMER_DELETE,
    CONTRACT_READ,
    CONTRACT_WRITE,
    CONTRACT_DELETE,
    BRANCH_MANAGE,
    DASHBOARD_READ,
    AUDIT_READ,
    NOTIFICATION_READ,
    DOCUMENT_READ,
    STAFF_MANAGE,
    /** Book vehicles, see and cancel one's own bookings, upload one's own documents. */
    BOOKING_OWN
}
