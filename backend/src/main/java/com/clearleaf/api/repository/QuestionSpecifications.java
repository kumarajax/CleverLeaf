package com.clearleaf.api.repository;

import com.clearleaf.api.entity.QuestionEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class QuestionSpecifications {
    private QuestionSpecifications() {
    }

    public static Specification<QuestionEntity> questionType(String value) {
        return equal("questionType", value);
    }

    public static Specification<QuestionEntity> tenant(UUID tenantId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<QuestionEntity> difficulty(String value) {
        return equal("difficulty", value);
    }

    public static Specification<QuestionEntity> workflowStatus(String value) {
        return equal("workflowStatus", value);
    }

    public static Specification<QuestionEntity> workflowStatuses(List<String> values) {
        return (root, query, criteriaBuilder) -> values == null || values.isEmpty()
                ? criteriaBuilder.conjunction()
                : root.get("workflowStatus").in(values);
    }

    public static Specification<QuestionEntity> textSearch(String value) {
        return (root, query, criteriaBuilder) -> value == null || value.isBlank()
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.like(criteriaBuilder.lower(root.get("questionText")), "%" + value.trim().toLowerCase() + "%");
    }

    public static Specification<QuestionEntity> assignedToAny(Collection<UUID> taxonomyNodeIds) {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);
            return root.join("taxonomyAssignments").join("taxonomyNode").get("id").in(taxonomyNodeIds);
        };
    }

    private static Specification<QuestionEntity> equal(String field, String value) {
        return (root, query, criteriaBuilder) -> value == null || value.isBlank()
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get(field), value.trim().toUpperCase());
    }
}
