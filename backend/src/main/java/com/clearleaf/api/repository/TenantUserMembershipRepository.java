package com.clearleaf.api.repository;

import com.clearleaf.api.entity.TenantUserMembershipEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantUserMembershipRepository extends JpaRepository<TenantUserMembershipEntity, UUID> {
    boolean existsByTenant_IdAndUserSubjectAndRoleAndStatus(UUID tenantId, String userSubject, String role, String status);

    boolean existsByTenant_IdAndUserSubjectAndStatus(UUID tenantId, String userSubject, String status);

    boolean existsByTenant_IdAndEmailIgnoreCaseAndStatus(UUID tenantId, String email, String status);

    Optional<TenantUserMembershipEntity> findByTenant_IdAndUserSubjectAndStatus(UUID tenantId, String userSubject, String status);

    List<TenantUserMembershipEntity> findByTenant_IdAndStatusOrderByEmailAsc(UUID tenantId, String status);

    long countByTenant_IdAndRoleAndStatus(UUID tenantId, String role, String status);

    List<TenantUserMembershipEntity> findByUserSubjectAndStatusOrderByTenant_TenantNameAsc(String userSubject, String status);
}
