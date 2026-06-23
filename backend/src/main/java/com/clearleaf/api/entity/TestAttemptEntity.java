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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "test_attempt")
public class TestAttemptEntity {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_subject", nullable = false, length = 256)
    private String studentSubject;

    @Column(name = "test_name", nullable = false, length = 256)
    private String testName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "taxonomy_node_id", nullable = false)
    private TaxonomyNodeEntity taxonomyNode;

    @Column(nullable = false, length = 16)
    private String difficulty;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "score_points")
    private Integer scorePoints;

    @Column(name = "max_points", nullable = false)
    private int maxPoints;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType = "RANDOM";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private AssignedTestAssignmentEntity assignment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("questionOrder ASC")
    private List<TestAttemptQuestionEntity> questions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (tenantId == null) {
            if (assignment != null) {
                tenantId = assignment.getTenantId();
            } else if (taxonomyNode != null) {
                tenantId = taxonomyNode.getTenantId();
            }
        }
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
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getStudentSubject() { return studentSubject; }
    public void setStudentSubject(String studentSubject) { this.studentSubject = studentSubject; }
    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }
    public TaxonomyNodeEntity getTaxonomyNode() { return taxonomyNode; }
    public void setTaxonomyNode(TaxonomyNodeEntity taxonomyNode) { this.taxonomyNode = taxonomyNode; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Integer getScorePoints() { return scorePoints; }
    public void setScorePoints(Integer scorePoints) { this.scorePoints = scorePoints; }
    public int getMaxPoints() { return maxPoints; }
    public void setMaxPoints(int maxPoints) { this.maxPoints = maxPoints; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public AssignedTestAssignmentEntity getAssignment() { return assignment; }
    public void setAssignment(AssignedTestAssignmentEntity assignment) { this.assignment = assignment; }
    public List<TestAttemptQuestionEntity> getQuestions() { return questions; }
}
