package com.clearleaf.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantMembershipAdminResponse(
        UUID membershipId,
        UUID tenantId,
        String email,
        String role,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
