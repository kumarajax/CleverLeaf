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
import java.util.UUID;

@Entity
@Table(name = "question_tag")
public class QuestionTagEntity {
    @EmbeddedId
    private QuestionTagId id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("questionId")
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    @PrePersist
    void onCreate() {
        if (tenantId == null && question != null) {
            tenantId = question.getTenantId();
        }
    }

    public void setId(QuestionTagId id) { this.id = id; }
    public QuestionTagId getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setQuestion(QuestionEntity question) { this.question = question; }
}
