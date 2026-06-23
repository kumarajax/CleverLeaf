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
@Table(name = "admin_test_version")
public class AdminTestVersionEntity {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private AdminTestEntity test;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "time_allowed_seconds", nullable = false)
    private int timeAllowedSeconds;

    @Column(name = "available_from")
    private Instant availableFrom;

    @Column(name = "available_until")
    private Instant availableUntil;

    @Column(name = "frozen_at", nullable = false)
    private Instant frozenAt;

    @Column(name = "results_published_at")
    private Instant resultsPublishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("questionOrder ASC")
    private List<AdminTestVersionQuestionEntity> questions = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (tenantId == null && test != null) {
            tenantId = test.getTenantId();
        }
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (frozenAt == null) frozenAt = now;
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
    public AdminTestEntity getTest() { return test; }
    public void setTest(AdminTestEntity test) { this.test = test; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public int getTimeAllowedSeconds() { return timeAllowedSeconds; }
    public void setTimeAllowedSeconds(int timeAllowedSeconds) { this.timeAllowedSeconds = timeAllowedSeconds; }
    public Instant getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(Instant availableFrom) { this.availableFrom = availableFrom; }
    public Instant getAvailableUntil() { return availableUntil; }
    public void setAvailableUntil(Instant availableUntil) { this.availableUntil = availableUntil; }
    public Instant getResultsPublishedAt() { return resultsPublishedAt; }
    public void setResultsPublishedAt(Instant resultsPublishedAt) { this.resultsPublishedAt = resultsPublishedAt; }
    public List<AdminTestVersionQuestionEntity> getQuestions() { return questions; }
}
