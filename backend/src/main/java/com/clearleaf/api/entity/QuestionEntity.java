package com.clearleaf.api.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "question")
public class QuestionEntity {
    @Id
    private UUID id;

    @Column(name = "question_type", nullable = false, length = 32)
    private String questionType;

    @Column(nullable = false, length = 16)
    private String difficulty;

    @Column(name = "workflow_status", nullable = false, length = 32)
    private String workflowStatus;

    @Column(nullable = false, length = 32)
    private String language = "English";

    @Column(name = "question_text", nullable = false)
    private String questionText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_taxonomy_node_id")
    private TaxonomyNodeEntity rootTaxonomyNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_taxonomy_node_id")
    private TaxonomyNodeEntity childTaxonomyNode;

    @Column(name = "normalized_question_text")
    private String normalizedQuestionText;

    @Column(name = "external_key", length = 128)
    private String externalKey;

    private String explanation;

    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "license_category", length = 64)
    private String licenseCategory;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType = "ORIGINAL";

    @Column(name = "created_by", nullable = false, length = 256)
    private String createdBy;

    @Column(name = "updated_by", length = 256)
    private String updatedBy;

    @Version
    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 100)
    private List<QuestionOptionEntity> options = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 100)
    private List<QuestionAnswerEntity> answers = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<QuestionTaxonomyNodeEntity> taxonomyAssignments = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<QuestionTagEntity> tags = new ArrayList<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<QuestionWorkflowEventEntity> workflowEvents = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getWorkflowStatus() { return workflowStatus; }
    public void setWorkflowStatus(String workflowStatus) { this.workflowStatus = workflowStatus; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public TaxonomyNodeEntity getRootTaxonomyNode() { return rootTaxonomyNode; }
    public void setRootTaxonomyNode(TaxonomyNodeEntity rootTaxonomyNode) { this.rootTaxonomyNode = rootTaxonomyNode; }
    public TaxonomyNodeEntity getChildTaxonomyNode() { return childTaxonomyNode; }
    public void setChildTaxonomyNode(TaxonomyNodeEntity childTaxonomyNode) { this.childTaxonomyNode = childTaxonomyNode; }
    public String getNormalizedQuestionText() { return normalizedQuestionText; }
    public void setNormalizedQuestionText(String normalizedQuestionText) { this.normalizedQuestionText = normalizedQuestionText; }
    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }
    public String getLicenseCategory() { return licenseCategory; }
    public void setLicenseCategory(String licenseCategory) { this.licenseCategory = licenseCategory; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<QuestionOptionEntity> getOptions() { return options; }
    public List<QuestionAnswerEntity> getAnswers() { return answers; }
    public List<QuestionTaxonomyNodeEntity> getTaxonomyAssignments() { return taxonomyAssignments; }
    public List<QuestionTagEntity> getTags() { return tags; }
    public List<QuestionWorkflowEventEntity> getWorkflowEvents() { return workflowEvents; }
}
