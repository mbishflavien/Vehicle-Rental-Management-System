package com.vrms.controller;

import com.vrms.dto.AuthResponse;
import com.vrms.dto.LoginRequest;
import com.vrms.dto.RegisterRequest;
import com.vrms.dto.UserView;
import com.vrms.exception.ApiException;
import com.vrms.security.CurrentUser;
import com.vrms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserView me() {
        return authService.me(CurrentUser.get()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue")));
    }
}
