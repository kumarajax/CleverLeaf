package com.clearleaf.api;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantInvitationResponse invite(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateTenantInvitationRequest request) {
        UUID tenantId = tenantAuthorization.tenantId(tenantHeader);
        return tenants.invite(tenantId, jwt.getSubject(), request);
    }
}
