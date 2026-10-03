package com.vrms.dto;

/** OAuth2-style token response: the bearer access token, its type and lifetime, and the user it belongs to. */
public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserView user) {
}
