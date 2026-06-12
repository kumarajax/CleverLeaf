package com.clearleaf.api.repository;

import com.clearleaf.api.entity.TaxonomyNodeEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaxonomyNodeRepository extends JpaRepository<TaxonomyNodeEntity, UUID>, JpaSpecificationExecutor<TaxonomyNodeEntity> {
    List<TaxonomyNodeEntity> findByParentNode_IdOrderBySortOrderAscDisplayNameAsc(UUID parentId);

    List<TaxonomyNodeEntity> findByParentNode_IdAndTenantIdOrderBySortOrderAscDisplayNameAsc(UUID parentId, UUID tenantId);

    List<TaxonomyNodeEntity> findByParentNode_IdAndIdNotOrderBySortOrderAscDisplayNameAsc(UUID parentId, UUID id);

    List<TaxonomyNodeEntity> findByParentNode_IdAndTenantIdAndIdNotOrderBySortOrderAscDisplayNameAsc(UUID parentId, UUID tenantId, UUID id);

    List<TaxonomyNodeEntity> findByIdInOrderBySortOrderAscDisplayNameAsc(List<UUID> ids);

    Optional<TaxonomyNodeEntity> findByExternalKey(String externalKey);

    Optional<TaxonomyNodeEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByRootTaxonomyNode_IdAndNodeKeyAndIdNot(UUID rootTaxonomyNodeId, String nodeKey, UUID id);

    boolean existsByRootTaxonomyNode_IdAndTenantIdAndNodeKeyAndIdNot(UUID rootTaxonomyNodeId, UUID tenantId, String nodeKey, UUID id);

    boolean existsByRootTaxonomyNode_IdAndNodeKeyAndIdNotIn(UUID rootTaxonomyNodeId, String nodeKey, Collection<UUID> ids);

    boolean existsByRootTaxonomyNode_IdAndTenantIdAndNodeKeyAndIdNotIn(UUID rootTaxonomyNodeId, UUID tenantId, String nodeKey, Collection<UUID> ids);

    boolean existsByLevelType_Id(UUID levelTypeId);

    boolean existsByParentNode_Id(UUID parentId);

    boolean existsByParentNode_IdAndTenantId(UUID parentId, UUID tenantId);
}
