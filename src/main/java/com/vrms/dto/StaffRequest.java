package com.vrms.dto;

import com.vrms.model.Role;
import jakarta.validation.constraints.*;

/**
 * Create or update a staff account. On update the email can't change and the password is optional
 * (a new one resets it).
 */
public record StaffRequest(
        @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Email address is not valid") String email,
        @Size(max = 80) String jobTitle,
        @NotNull(message = "Choose a role") Role role,
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers") String password,
        Boolean enabled) {
}
