package com.vrms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "customer_id", updatable = false, nullable = false)
    private UUID customerId;

    @NotBlank(message = "Full name is required")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email address is not valid")
    @Column(unique = true)
    private String email;

    @Pattern(regexp = "^$|\\+?[0-9 ]{9,16}", message = "Phone number is not valid, e.g. +250 788 123 456")
    private String phoneNumber;

    @NotBlank(message = "Driver license is required")
    @Pattern(regexp = "DL-[A-Z0-9-]+", message = "Driver License must start with 'DL-', e.g. DL-48219")
    @Column(unique = true, nullable = false)
    private String driverLicenseNumber;

    /** Login account, if the customer registered online. Walk-in customers added by staff have none. */
    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Customer() {}

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName == null ? null : fullName.trim(); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim().toLowerCase(); }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber == null ? null : phoneNumber.trim(); }
    public String getDriverLicenseNumber() { return driverLicenseNumber; }
    public void setDriverLicenseNumber(String driverLicenseNumber) {
        this.driverLicenseNumber = driverLicenseNumber == null ? null : driverLicenseNumber.trim().toUpperCase();
    }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Instant getCreatedAt() { return createdAt; }

    @JsonProperty(value = "hasAccount", access = JsonProperty.Access.READ_ONLY)
    public boolean hasAccount() { return user != null; }
}
