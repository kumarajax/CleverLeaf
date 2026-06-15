package com.clearleaf.api;

import com.clearleaf.api.entity.TenantInvitationEntity;
import com.clearleaf.api.entity.TenantUserMembershipEntity;
import com.clearleaf.api.repository.TenantInvitationRepository;
import com.clearleaf.api.repository.TenantUserMembershipRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserTenantInvitationService {
    private final TenantInvitationRepository invitations;
    private final TenantUserMembershipRepository memberships;

    public UserTenantInvitationService(
            TenantInvitationRepository invitations,
            TenantUserMembershipRepository memberships) {
        this.invitations = invitations;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public List<UserTenantInvitationResponse> pending(String email) {
        return invitations.findByEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(normalizeEmail(email), "PENDING").stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TenantMembershipResponse accept(UUID invitationId, String subject, String email) {
        TenantInvitationEntity invitation = pendingInvitation(invitationId, email);
        if (invitation.getExpiresAt().isBefore(OffsetDateTime.now())) {
            invitation.setStatus("EXPIRED");
            invitations.save(invitation);
            throw new ResponseStatusException(HttpStatus.GONE, "Tenant invitation has expired");
        }
        if (memberships.existsByTenant_IdAndUserSubjectAndStatus(invitation.getTenant().getId(), subject, "ACTIVE")
                || memberships.existsByTenant_IdAndEmailIgnoreCaseAndStatus(invitation.getTenant().getId(), invitation.getEmail(), "ACTIVE")) {
            invitation.setStatus("ACCEPTED");
            invitation.setAcceptedAt(OffsetDateTime.now());
            invitations.save(invitation);
            return new TenantMembershipResponse(invitation.getTenant().getId(), invitation.getTenant().getTenantName(), invitation.getRole(), "ACTIVE");
        }
        TenantUserMembershipEntity membership = new TenantUserMembershipEntity();
        membership.setId(UUID.randomUUID());
        membership.setTenant(invitation.getTenant());
        membership.setUserSubject(subject);
        membership.setEmail(invitation.getEmail());
        membership.setRole(invitation.getRole());
        membership.setStatus("ACTIVE");
        membership.setCreatedBySubject(invitation.getCreatedBySubject());
        memberships.save(membership);

        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAt(OffsetDateTime.now());
        invitations.save(invitation);
        return new TenantMembershipResponse(invitation.getTenant().getId(), invitation.getTenant().getTenantName(), membership.getRole(), membership.getStatus());
    }

    @Transactional
    public UserTenantInvitationResponse reject(UUID invitationId, String email) {
        TenantInvitationEntity invitation = pendingInvitation(invitationId, email);
        invitation.setStatus("REVOKED");
        invitations.save(invitation);
        return toResponse(invitation);
    }

    private TenantInvitationEntity pendingInvitation(UUID invitationId, String email) {
        String normalizedEmail = normalizeEmail(email);
        TenantInvitationEntity invitation = invitations.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant invitation was not found"));
        if (!"PENDING".equals(invitation.getStatus()) || !invitation.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant invitation was not found");
        }
        return invitation;
    }

    private UserTenantInvitationResponse toResponse(TenantInvitationEntity invitation) {
        return new UserTenantInvitationResponse(
                invitation.getId(),
                invitation.getTenant().getId(),
                invitation.getTenant().getTenantName(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt());
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank() || !value.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        return value.trim().toLowerCase();
    }
}
