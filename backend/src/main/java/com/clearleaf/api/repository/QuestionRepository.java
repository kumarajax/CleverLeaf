package com.clearleaf.api.repository;

import com.clearleaf.api.entity.QuestionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<QuestionEntity, UUID>, JpaSpecificationExecutor<QuestionEntity> {
    Optional<QuestionEntity> findByExternalKey(String externalKey);

    Optional<QuestionEntity> findByExternalKeyAndTenantId(String externalKey, UUID tenantId);

    Optional<QuestionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<QuestionEntity> findByRootTaxonomyNode_IdAndChildTaxonomyNode_IdAndNormalizedQuestionTextAndTenantId(
            UUID rootTaxonomyNodeId,
            UUID childTaxonomyNodeId,
            String normalizedQuestionText,
            UUID tenantId);

    boolean existsByTaxonomyAssignments_TaxonomyNode_Id(UUID taxonomyNodeId);

    boolean existsByTaxonomyAssignments_TaxonomyNode_IdAndTenantId(UUID taxonomyNodeId, UUID tenantId);

    @Query("""
            select question
            from QuestionEntity question
            where exists (
                select 1
                from question.taxonomyAssignments assignment
                where assignment.taxonomyNode.id in :taxonomyNodeIds
            )
              and upper(question.difficulty) = :difficulty
              and upper(question.workflowStatus) in :workflowStatuses
              and question.tenantId = :tenantId
            order by function('random')
            """)
    List<QuestionEntity> findRandomEligibleForTest(
            @Param("taxonomyNodeIds") Collection<UUID> taxonomyNodeIds,
            @Param("difficulty") String difficulty,
            @Param("workflowStatuses") Collection<String> workflowStatuses,
            @Param("tenantId") UUID tenantId,
            Pageable pageable);

    @Query("""
            select question
            from QuestionEntity question
            where exists (
                select 1
                from question.taxonomyAssignments assignment
                where assignment.taxonomyNode.id in :taxonomyNodeIds
            )
              and upper(question.difficulty) in :difficulties
              and upper(question.workflowStatus) in :workflowStatuses
              and question.tenantId = :tenantId
            order by function('random')
            """)
    List<QuestionEntity> findRandomEligibleForTestDifficulties(
            @Param("taxonomyNodeIds") Collection<UUID> taxonomyNodeIds,
            @Param("difficulties") Collection<String> difficulties,
            @Param("workflowStatuses") Collection<String> workflowStatuses,
            @Param("tenantId") UUID tenantId,
            Pageable pageable);

    @Query("""
            select count(distinct question.id)
            from QuestionEntity question
            where exists (
                select 1
                from question.taxonomyAssignments assignment
                where assignment.taxonomyNode.id in :taxonomyNodeIds
            )
              and upper(question.workflowStatus) in :workflowStatuses
              and question.tenantId = :tenantId
            """)
    long countTestableByTaxonomyNodeIds(
            @Param("taxonomyNodeIds") Collection<UUID> taxonomyNodeIds,
            @Param("workflowStatuses") Collection<String> workflowStatuses,
            @Param("tenantId") UUID tenantId);
}
