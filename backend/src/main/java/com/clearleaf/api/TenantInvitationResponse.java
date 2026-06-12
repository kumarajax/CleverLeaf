package com.clearleaf.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantInvitationResponse(
        UUID invitationId,
        UUID tenantId,
        String email,
        String role,
        String status,
        OffsetDateTime expiresAt) {
}
