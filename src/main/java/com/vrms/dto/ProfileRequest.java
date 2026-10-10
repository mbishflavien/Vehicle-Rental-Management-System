package com.vrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Details a customer must add before booking, e.g. after signing up with Google or GitHub. */
public record ProfileRequest(
        @Pattern(regexp = "^$|\\+?[0-9 ]{9,16}", message = "Phone number is not valid, e.g. +250 788 123 456") String phoneNumber,
        @NotBlank(message = "Driver license is required")
        @Pattern(regexp = "(?i)DL-[A-Z0-9-]+", message = "Driver License must start with 'DL-', e.g. DL-48219") String driverLicenseNumber) {
}
