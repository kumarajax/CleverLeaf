package com.clearleaf.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tenant")
public class TenantAdminController {
    private final TenantAdminService tenants;
    private final TenantAuthorizationService tenantAuthorization;

    public TenantAdminController(TenantAdminService tenants, TenantAuthorizationService tenantAuthorization) {
        this.tenants = tenants;
        this.tenantAuthorization = tenantAuthorization;
    }

    @GetMapping("/memberships")
    public List<TenantMembershipAdminResponse> memberships(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID tenantId = tenantAuthorization.tenantId(tenantHeader);
        return tenants.memberships(tenantId, jwt.getSubject(), tenantAuthorization.canUseAdminApi(authentication, tenantId));
    }

    @PutMapping("/memberships/{membershipId}/role")
    public TenantMembershipAdminResponse updateMembershipRole(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable("membershipId") UUID membershipId,
            @RequestBody UpdateTenantMembershipRoleRequest request) {
        UUID tenantId = tenantAuthorization.tenantId(tenantHeader);
        return tenants.updateMembershipRole(
                tenantId,
                membershipId,
                jwt.getSubject(),
                tenantAuthorization.canUseAdminApi(authentication, tenantId),
                request);
    }

    @GetMapping("/invitations")
    public List<TenantInvitationResponse> invitations(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        UUID tenantId = tenantAuthorization.tenantId(tenantHeader);
        return tenants.invitations(tenantId, jwt.getSubject(), tenantAuthorization.canUseAdminApi(authentication, tenantId));
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantInvitationResponse invite(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestBody CreateTenantInvitationRequest request) {
        UUID tenantId = tenantAuthorization.tenantId(tenantHeader);
        return tenants.invite(tenantId, jwt.getSubject(), tenantAuthorization.canUseAdminApi(authentication, tenantId), request);
    }
}
