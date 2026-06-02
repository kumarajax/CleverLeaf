package com.clearleaf.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "taxonomy_edition_state")
public class TaxonomyEditionStateEntity {
    @Id
    @Column(name = "curriculum_id")
    private UUID curriculumId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_edition_id", nullable = false)
    private TaxonomyNodeEntity activeEditionNode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TaxonomyEditionStateEntity() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getCurriculumId() {
        return curriculumId;
    }

    public void setCurriculumId(UUID curriculumId) {
        this.curriculumId = curriculumId;
    }

    public TaxonomyNodeEntity getActiveEditionNode() {
        return activeEditionNode;
    }

    public void setActiveEditionNode(TaxonomyNodeEntity activeEditionNode) {
        this.activeEditionNode = activeEditionNode;
    }
}
