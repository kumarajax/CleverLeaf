package com.clearleaf.api;

import java.util.UUID;

public record TenantMembershipResponse(
        UUID tenantId,
        String tenantName,
        String role,
        String status) {
}
