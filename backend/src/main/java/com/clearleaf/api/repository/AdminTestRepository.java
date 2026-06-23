package com.clearleaf.api.repository;

import com.clearleaf.api.entity.AdminTestEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminTestRepository extends JpaRepository<AdminTestEntity, UUID> {
    Optional<AdminTestEntity> findByPublicKeyIgnoreCase(String publicKey);
    Optional<AdminTestEntity> findByPublicKeyIgnoreCaseAndTenantId(String publicKey, UUID tenantId);
    List<AdminTestEntity> findByCreatorSubjectOrderByCreatedAtDesc(String creatorSubject);
    List<AdminTestEntity> findByCreatorSubjectAndTenantIdOrderByCreatedAtDesc(String creatorSubject, UUID tenantId);
}
