package com.vrms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Sign-in is handled by JwtAuthFilter, so Spring's default in-memory user (and its generated password) is not needed.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class VehicleRentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(VehicleRentalApplication.class, args);
    }
}