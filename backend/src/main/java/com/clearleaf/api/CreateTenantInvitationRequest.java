package com.clearleaf.api;

public record CreateTenantInvitationRequest(
        String email,
        String role) {
}
