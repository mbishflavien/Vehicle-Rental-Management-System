package com.vrms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sign-up form on the "Create account" tab. Creates a login plus a customer profile. */
public record RegisterRequest(
        @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Email address is not valid") String email,
        @Pattern(regexp = "^$|\\+?[0-9 ]{9,16}", message = "Phone number is not valid, e.g. +250 788 123 456") String phoneNumber,
        @NotBlank(message = "Driver license is required")
        @Pattern(regexp = "(?i)DL-[A-Z0-9-]+", message = "Driver License must start with 'DL-', e.g. DL-48219") String driverLicenseNumber,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers") String password) {
}
