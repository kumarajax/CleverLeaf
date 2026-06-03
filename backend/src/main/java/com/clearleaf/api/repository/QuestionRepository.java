package com.clearleaf.api.repository;

import com.clearleaf.api.entity.QuestionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface QuestionRepository extends JpaRepository<QuestionEntity, UUID>, JpaSpecificationExecutor<QuestionEntity> {
    Optional<QuestionEntity> findByExternalKey(String externalKey);

    Optional<QuestionEntity> findByRootTaxonomyNode_IdAndChildTaxonomyNode_IdAndNormalizedQuestionText(
            UUID rootTaxonomyNodeId,
            UUID childTaxonomyNodeId,
            String normalizedQuestionText);

    boolean existsByTaxonomyAssignments_TaxonomyNode_Id(UUID taxonomyNodeId);
}
