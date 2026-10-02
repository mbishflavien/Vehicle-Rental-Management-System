package com.vrms.dto;

public record AuthResponse(String token, long expiresInSeconds, UserView user) {
}
