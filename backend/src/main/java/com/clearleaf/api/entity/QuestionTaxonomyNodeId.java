package com.clearleaf.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class QuestionTaxonomyNodeId implements Serializable {
    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "taxonomy_node_id")
    private UUID taxonomyNodeId;

    public QuestionTaxonomyNodeId() {
    }

    public QuestionTaxonomyNodeId(UUID questionId, UUID taxonomyNodeId) {
        this.questionId = questionId;
        this.taxonomyNodeId = taxonomyNodeId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QuestionTaxonomyNodeId that)) return false;
        return Objects.equals(questionId, that.questionId) && Objects.equals(taxonomyNodeId, that.taxonomyNodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, taxonomyNodeId);
    }
}
