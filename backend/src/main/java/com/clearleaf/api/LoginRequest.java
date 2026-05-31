package com.clearleaf.api;

public record LoginRequest(
        String email,
        String password) {
}
