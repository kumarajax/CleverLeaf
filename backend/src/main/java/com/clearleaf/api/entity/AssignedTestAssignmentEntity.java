package com.clearleaf.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assigned_test_assignment")
public class AssignedTestAssignmentEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private AdminTestVersionEntity version;

    @Column(name = "student_subject", nullable = false, length = 256)
    private String studentSubject;

    @Column(nullable = false, length = 32)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_job_id")
    private AssignedTestImportJobEntity importJob;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reset_at")
    private Instant resetAt;

    @PrePersist
    void onCreate() {
        if (assignedAt == null) assignedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public AdminTestVersionEntity getVersion() { return version; }
    public void setVersion(AdminTestVersionEntity version) { this.version = version; }
    public String getStudentSubject() { return studentSubject; }
    public void setStudentSubject(String studentSubject) { this.studentSubject = studentSubject; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public AssignedTestImportJobEntity getImportJob() { return importJob; }
    public void setImportJob(AssignedTestImportJobEntity importJob) { this.importJob = importJob; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getResetAt() { return resetAt; }
    public void setResetAt(Instant resetAt) { this.resetAt = resetAt; }
}
