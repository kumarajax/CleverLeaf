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

    Optional<QuestionEntity> findByRootTaxonomyNode_IdAndChildTaxonomyNode_IdAndNormalizedQuestionText(
            UUID rootTaxonomyNodeId,
            UUID childTaxonomyNodeId,
            String normalizedQuestionText);

    boolean existsByTaxonomyAssignments_TaxonomyNode_Id(UUID taxonomyNodeId);

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
            order by function('random')
            """)
    List<QuestionEntity> findRandomEligibleForTest(
            @Param("taxonomyNodeIds") Collection<UUID> taxonomyNodeIds,
            @Param("difficulty") String difficulty,
            @Param("workflowStatuses") Collection<String> workflowStatuses,
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
            """)
    long countTestableByTaxonomyNodeIds(
            @Param("taxonomyNodeIds") Collection<UUID> taxonomyNodeIds,
            @Param("workflowStatuses") Collection<String> workflowStatuses);
}
