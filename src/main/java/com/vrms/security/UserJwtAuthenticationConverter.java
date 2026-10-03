package com.vrms.security;

import com.vrms.model.User;
import com.vrms.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Turns a validated access token into the signed-in {@link User}. Authorities come from the user's
 * current role in the database rather than from the token, so a role change or a disabled account
 * takes effect on the very next request.
 */
@Component
public class UserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public UserJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = parse(jwt.getSubject())
                .flatMap(userRepository::findById)
                .filter(User::isEnabled)
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_token", "Account not found or disabled", null)));
        return new UsernamePasswordAuthenticationToken(user, jwt, user.getRole().authorities());
    }

    private static Optional<UUID> parse(String subject) {
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
