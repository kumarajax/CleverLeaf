package com.clearleaf.api.repository;

import com.clearleaf.api.entity.TaxonomyEditionStateEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxonomyEditionStateRepository extends JpaRepository<TaxonomyEditionStateEntity, UUID> {
    Optional<TaxonomyEditionStateEntity> findByCurriculumId(UUID curriculumId);
    Optional<TaxonomyEditionStateEntity> findByCurriculumIdAndTenantId(UUID curriculumId, UUID tenantId);
}
