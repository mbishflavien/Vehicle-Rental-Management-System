package com.vrms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vrms.exception.GlobalExceptionHandler;
import com.vrms.security.JwtService;
import com.vrms.security.UserJwtAuthenticationConverter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Security model:
 *  - Authentication: the REST API is an OAuth2 resource server. Clients send an OAuth2 bearer
 *    access token (signed JWT, validated for signature, expiry, issuer and audience).
 *  - Authorization (RBAC): coarse rules per URL here, and a permission check on every protected
 *    endpoint via @PreAuthorize (see Role and Permission).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Value("${vrms.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurity(HttpSecurity http, UserJwtAuthenticationConverter jwtConverter) throws Exception {
        http
            .securityMatcher("/api/**")
            // Stateless bearer tokens in the Authorization header: no cookies, so CSRF does not apply.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: sign in / register, browsing the fleet and branches
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/providers").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/vehicles", "/api/vehicles/*", "/api/branches").permitAll()
                // Customers' own area
                .requestMatchers("/api/me/**").hasRole("CUSTOMER")
                // Staff console: any staff role here; each endpoint then checks its specific permission
                .requestMatchers("/api/vehicles/**", "/api/customers/**", "/api/contracts/**", "/api/branches/**",
                                 "/api/dashboard/**", "/api/logs/**", "/api/staff/**", "/api/notifications/**",
                                 "/api/documents/**").hasAnyRole("ADMIN", "AGENT")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                .authenticationEntryPoint((req, res, ex) ->
                        writeError(res, HttpStatus.UNAUTHORIZED, "Please sign in to continue")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        writeError(res, HttpStatus.UNAUTHORIZED, "Please sign in to continue"))
                .accessDeniedHandler((req, res, ex) ->
                        writeError(res, HttpStatus.FORBIDDEN, "You do not have permission to do that")));
        return http.build();
    }

    /** Everything that isn't the API: the React app and its static files. */
    @Bean
    @Order(3)
    public SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' https: data: blob:; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                    + "font-src 'self' https://fonts.gstatic.com; script-src 'self'; connect-src 'self'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.decoder();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void writeError(HttpServletResponse res, HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getOutputStream(), GlobalExceptionHandler.errorBody(status, message, Map.of()));
    }
}
