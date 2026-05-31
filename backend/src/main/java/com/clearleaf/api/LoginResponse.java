package com.clearleaf.api;

public record LoginResponse(
        String status,
        String message,
        String email,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {
}
