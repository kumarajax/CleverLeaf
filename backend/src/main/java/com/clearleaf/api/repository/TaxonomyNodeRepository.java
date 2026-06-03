package com.clearleaf.api.repository;

import com.clearleaf.api.entity.TaxonomyNodeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaxonomyNodeRepository extends JpaRepository<TaxonomyNodeEntity, UUID>, JpaSpecificationExecutor<TaxonomyNodeEntity> {
    List<TaxonomyNodeEntity> findByParentNode_IdOrderBySortOrderAscDisplayNameAsc(UUID parentId);

    List<TaxonomyNodeEntity> findByParentNode_IdAndIdNotOrderBySortOrderAscDisplayNameAsc(UUID parentId, UUID id);

    List<TaxonomyNodeEntity> findByIdInOrderBySortOrderAscDisplayNameAsc(List<UUID> ids);

    Optional<TaxonomyNodeEntity> findByExternalKey(String externalKey);

    boolean existsByLevelType_Id(UUID levelTypeId);

    boolean existsByParentNode_Id(UUID parentId);
}
