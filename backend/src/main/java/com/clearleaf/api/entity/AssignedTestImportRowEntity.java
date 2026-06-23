package com.clearleaf.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "assigned_test_import_row")
public class AssignedTestImportRowEntity {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private AssignedTestImportJobEntity job;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "test_public_key", length = 128)
    private String testPublicKey;

    @Column(name = "student_subject", length = 256)
    private String studentSubject;

    @Column(nullable = false, length = 32)
    private String status;

    @Column
    private String message;

    @PrePersist
    void onCreate() {
        if (tenantId == null && job != null) {
            tenantId = job.getTenantId();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public AssignedTestImportJobEntity getJob() { return job; }
    public void setJob(AssignedTestImportJobEntity job) { this.job = job; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public String getTestPublicKey() { return testPublicKey; }
    public void setTestPublicKey(String testPublicKey) { this.testPublicKey = testPublicKey; }
    public String getStudentSubject() { return studentSubject; }
    public void setStudentSubject(String studentSubject) { this.studentSubject = studentSubject; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
