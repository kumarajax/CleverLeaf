package com.clearleaf.api.repository;

import com.clearleaf.api.entity.TenantInvitationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantInvitationRepository extends JpaRepository<TenantInvitationEntity, UUID> {
    Optional<TenantInvitationEntity> findFirstByTenant_TenantNameIgnoreCaseAndEmailIgnoreCaseAndStatus(
            String tenantName,
            String email,
            String status);

    List<TenantInvitationEntity> findByEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(String email, String status);

    List<TenantInvitationEntity> findByTenant_IdOrderByCreatedAtDesc(UUID tenantId);
}
