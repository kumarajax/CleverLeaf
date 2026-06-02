package com.clearleaf.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "question_taxonomy_node")
public class QuestionTaxonomyNodeEntity {
    @EmbeddedId
    private QuestionTaxonomyNodeId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("questionId")
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("taxonomyNodeId")
    @JoinColumn(name = "taxonomy_node_id", nullable = false)
    private TaxonomyNodeEntity taxonomyNode;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public void setId(QuestionTaxonomyNodeId id) { this.id = id; }
    public void setQuestion(QuestionEntity question) { this.question = question; }
    public TaxonomyNodeEntity getTaxonomyNode() { return taxonomyNode; }
    public void setTaxonomyNode(TaxonomyNodeEntity taxonomyNode) { this.taxonomyNode = taxonomyNode; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
}
