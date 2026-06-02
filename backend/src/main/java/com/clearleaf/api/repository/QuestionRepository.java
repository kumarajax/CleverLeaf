package com.clearleaf.api.repository;

import com.clearleaf.api.entity.QuestionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface QuestionRepository extends JpaRepository<QuestionEntity, UUID>, JpaSpecificationExecutor<QuestionEntity> {
    boolean existsByTaxonomyAssignments_TaxonomyNode_Id(UUID taxonomyNodeId);
}
