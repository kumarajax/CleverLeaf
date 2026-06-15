package com.clearleaf.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/tenant-invitations")
public class UserTenantInvitationController {
    private final UserTenantInvitationService invitations;

    public UserTenantInvitationController(UserTenantInvitationService invitations) {
        this.invitations = invitations;
    }

    @GetMapping
    public List<UserTenantInvitationResponse> pending(@AuthenticationPrincipal Jwt jwt) {
        return invitations.pending(email(jwt));
    }

    @PostMapping("/{invitationId}/accept")
    public TenantMembershipResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable("invitationId") UUID invitationId) {
        return invitations.accept(invitationId, jwt.getSubject(), email(jwt));
    }

    @PostMapping("/{invitationId}/reject")
    public UserTenantInvitationResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable("invitationId") UUID invitationId) {
        return invitations.reject(invitationId, email(jwt));
    }

    private String email(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) return email;
        return jwt.getClaimAsString("preferred_username");
    }
}
