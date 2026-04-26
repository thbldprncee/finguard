package com.finguard.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        String tokenType,
        UUID userId,
        String email,
        String role,
        long expiresIn
) {
    public static AuthResponse of(String token, UUID userId, String email, String role, long expiresIn) {
        return new AuthResponse(token, "Bearer", userId, email, role, expiresIn);
    }
}
