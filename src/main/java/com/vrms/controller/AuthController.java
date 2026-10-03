package com.vrms.controller;

import com.vrms.config.OAuth2LoginConfig;
import com.vrms.dto.AuthResponse;
import com.vrms.dto.LoginRequest;
import com.vrms.dto.PasswordChangeRequest;
import com.vrms.dto.RegisterRequest;
import com.vrms.dto.UserView;
import com.vrms.exception.ApiException;
import com.vrms.model.User;
import com.vrms.security.CurrentUser;
import com.vrms.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Authentication", description = "Sign in, register, OAuth2 providers and the current user")
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final OAuth2LoginConfig oauth2;

    public AuthController(AuthService authService, OAuth2LoginConfig oauth2) {
        this.authService = authService;
        this.oauth2 = oauth2;
    }

    /** OAuth2 identity providers available for "Continue with …" (start at /oauth2/authorization/{id}). */
    @GetMapping("/providers")
    public List<String> providers() {
        return oauth2.providers();
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** Email/password sign-in. Returns an OAuth2 bearer access token. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    @GetMapping("/me")
    public UserView me() {
        return authService.me(current());
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(current(), request);
        return ResponseEntity.noContent().build();
    }

    private static User current() {
        return CurrentUser.get().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue"));
    }
}
