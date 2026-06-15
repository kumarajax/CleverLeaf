package com.clearleaf.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserTenantInvitationResponse(
        UUID invitationId,
        UUID tenantId,
        String tenantName,
        String email,
        String role,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt) {
}
