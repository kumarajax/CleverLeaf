package com.clearleaf.api.repository;

import com.clearleaf.api.entity.TenantEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
    boolean existsByTenantNameIgnoreCase(String tenantName);

    Optional<TenantEntity> findByTenantNameIgnoreCase(String tenantName);
}
