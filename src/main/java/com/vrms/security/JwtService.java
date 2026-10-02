package com.vrms.security;

import com.vrms.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration lifetime;

    public JwtService(@Value("${vrms.jwt.secret}") String secret,
                      @Value("${vrms.jwt.expiration-minutes}") long expirationMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "vrms.jwt.secret is not set. Add it to src/main/resources/application-secrets.properties "
                    + "(see application-secrets.properties.example) or set the JWT_SECRET environment variable.");
        }
        byte[] bytes = Decoders.BASE64.decode(secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("vrms.jwt.secret must decode to at least 32 bytes (256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.lifetime = Duration.ofMinutes(expirationMinutes);
    }

    public String issueToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    /** Returns the user id from a valid, unexpired token, or empty if the token is bad. */
    public Optional<UUID> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long lifetimeSeconds() { return lifetime.toSeconds(); }
}
