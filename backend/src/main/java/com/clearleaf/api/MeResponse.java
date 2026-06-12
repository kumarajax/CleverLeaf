package com.clearleaf.api;

import java.util.List;
import java.util.Map;

public record MeResponse(
        String subject,
        String username,
        String email,
        String name,
        List<String> roles,
        Map<String, List<String>> clientRoles,
        List<TenantMembershipResponse> tenantMemberships,
        String issuer) {
}
