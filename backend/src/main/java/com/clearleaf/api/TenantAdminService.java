package com.clearleaf.api;

import com.clearleaf.api.entity.TenantEntity;
import com.clearleaf.api.entity.TenantInvitationEntity;
import com.clearleaf.api.repository.TenantInvitationRepository;
import com.clearleaf.api.repository.TenantRepository;
import com.clearleaf.api.repository.TenantUserMembershipRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantAdminService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TenantRepository tenants;
    private final TenantUserMembershipRepository memberships;
    private final TenantInvitationRepository invitations;

    public TenantAdminService(
            TenantRepository tenants,
            TenantUserMembershipRepository memberships,
            TenantInvitationRepository invitations) {
        this.tenants = tenants;
        this.memberships = memberships;
        this.invitations = invitations;
    }

    @Transactional(readOnly = true)
    public List<TenantMembershipAdminResponse> memberships(UUID tenantId, String actorSubject, boolean platformAdmin) {
        requireTenantAdmin(tenantId, actorSubject, platformAdmin);
        return memberships.findByTenant_IdAndStatusOrderByEmailAsc(tenantId, "ACTIVE").stream()
                .map(this::toMembershipResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TenantInvitationResponse> invitations(UUID tenantId, String actorSubject, boolean platformAdmin) {
        requireTenantAdmin(tenantId, actorSubject, platformAdmin);
        return invitations.findByTenant_IdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TenantMembershipAdminResponse updateMembershipRole(
            UUID tenantId,
            UUID membershipId,
            String actorSubject,
            boolean platformAdmin,
            UpdateTenantMembershipRoleRequest request) {
        requireTenantAdmin(tenantId, actorSubject, platformAdmin);
        String role = normalizeRole(request == null ? null : request.role());
        var membership = memberships.findById(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant membership was not found"));
        if (!membership.getTenant().getId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant membership was not found");
        }
        if (!"ACTIVE".equals(membership.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only active memberships can be updated");
        }
        if ("ADMIN".equals(membership.getRole()) && "STUDENT".equals(role)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant admin role cannot be removed");
        }
        membership.setRole(role);
        return toMembershipResponse(memberships.save(membership));
    }

    @Transactional
    public TenantInvitationResponse invite(UUID tenantId, String actorSubject, boolean platformAdmin, CreateTenantInvitationRequest request) {
        requireTenantAdmin(tenantId, actorSubject, platformAdmin);
        TenantEntity tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant was not found"));
        if (!"ACTIVE".equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant is not active");
        }
        String email = normalizeEmail(request == null ? null : request.email());
        String role = normalizeRole(request == null ? null : request.role());
        if (memberships.existsByTenant_IdAndEmailIgnoreCaseAndStatus(tenantId, email, "ACTIVE")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this tenant");
        }
        invitations.findFirstByTenant_TenantNameIgnoreCaseAndEmailIgnoreCaseAndStatus(tenant.getTenantName(), email, "PENDING")
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A tenant invitation is already pending for this email");
                });

        TenantInvitationEntity invitation = new TenantInvitationEntity();
        invitation.setId(UUID.randomUUID());
        invitation.setTenant(tenant);
        invitation.setEmail(email);
        invitation.setRole(role);
        invitation.setInviteTokenHash(hashToken(randomToken()));
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(OffsetDateTime.now().plusDays(7));
        invitation.setCreatedBySubject(actorSubject);
        invitations.save(invitation);
        return toResponse(invitation);
    }

    private void requireTenantAdmin(UUID tenantId, String actorSubject, boolean platformAdmin) {
        if (platformAdmin) return;
        if (!memberships.existsByTenant_IdAndUserSubjectAndRoleAndStatus(tenantId, actorSubject, "ADMIN", "ACTIVE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant admin access is required");
        }
    }

    private TenantMembershipAdminResponse toMembershipResponse(com.clearleaf.api.entity.TenantUserMembershipEntity membership) {
        return new TenantMembershipAdminResponse(
                membership.getId(),
                membership.getTenant().getId(),
                membership.getEmail(),
                membership.getRole(),
                membership.getStatus(),
                membership.getCreatedAt(),
                membership.getUpdatedAt());
    }

    private TenantInvitationResponse toResponse(TenantInvitationEntity invitation) {
        return new TenantInvitationResponse(
                invitation.getId(),
                invitation.getTenant().getId(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getExpiresAt());
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank() || !value.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        return value.trim().toLowerCase();
    }

    private String normalizeRole(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!normalized.equals("ADMIN") && !normalized.equals("STUDENT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role must be ADMIN or STUDENT");
        }
        return normalized;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to hash invitation token", ex);
        }
    }
}
