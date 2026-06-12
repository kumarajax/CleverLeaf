package com.clearleaf.api;

import com.clearleaf.api.entity.TenantEntity;
import com.clearleaf.api.repository.TenantUserMembershipRepository;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class TenantAuthorizationService {
    public static final String TENANT_HEADER = "X-CleverLeaf-Tenant-Id";

    private final TenantUserMembershipRepository memberships;

    public TenantAuthorizationService(TenantUserMembershipRepository memberships) {
        this.memberships = memberships;
    }

    public UUID tenantId(String rawTenantId) {
        if (rawTenantId == null || rawTenantId.isBlank()) {
            return TenantEntity.DEMO_TENANT_ID;
        }
        return UUID.fromString(rawTenantId.trim());
    }

    public boolean canUseAdminApi(Authentication authentication, UUID tenantId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (canUsePlatformAdminApi(authentication)) {
            return true;
        }
        return memberships.existsByTenant_IdAndUserSubjectAndRoleAndStatus(
                tenantId,
                authentication.getName(),
                "ADMIN",
                "ACTIVE");
    }

    public boolean canUsePlatformAdminApi(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && hasPlatformAdminRole(authentication.getAuthorities());
    }

    private boolean hasPlatformAdminRole(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_administrator")
                        || role.equals("ROLE_reviewer")
                        || role.equals("ROLE_content_creator"));
    }
}
