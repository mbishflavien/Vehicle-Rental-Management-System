package com.vrms.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.vrms.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Issues and validates the OAuth2 bearer access tokens (signed JWTs, HS256).
 * The API validates them as an OAuth2 resource server; see SecurityConfig.
 */
@Service
public class JwtService {

    public static final String ISSUER = "vrms";
    public static final String AUDIENCE = "vrms-api";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration lifetime;

    public JwtService(@Value("${vrms.jwt.secret}") String secret,
                      @Value("${vrms.jwt.expiration-minutes}") long expirationMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "vrms.jwt.secret is not set. Add it to src/main/resources/application-secrets.properties "
                    + "(see application-secrets.properties.example) or set the JWT_SECRET environment variable.");
        }
        byte[] bytes = Base64.getDecoder().decode(secret.trim());
        if (bytes.length < 32) {
            throw new IllegalStateException("vrms.jwt.secret must decode to at least 32 bytes (256 bits)");
        }
        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER), audience));
        this.decoder = nimbus;
        this.lifetime = Duration.ofMinutes(expirationMinutes);
    }

    public String issueToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(user.getUserId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("scope", String.join(" ", user.getRole().getPermissions().stream().map(Enum::name).toList()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public long lifetimeSeconds() { return lifetime.toSeconds(); }
}
