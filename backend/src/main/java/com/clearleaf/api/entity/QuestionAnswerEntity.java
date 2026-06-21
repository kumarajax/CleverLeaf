package com.clearleaf.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "question_answer")
public class QuestionAnswerEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    @Column(name = "answer_value")
    private String answerValue;

    @Column(name = "answer_media_object_key")
    private String answerMediaObjectKey;

    @Column(name = "answer_media_content_type", length = 128)
    private String answerMediaContentType;

    @Column(name = "answer_type", nullable = false, length = 32)
    private String answerType;

    @Column(name = "tolerance_value")
    private BigDecimal toleranceValue;

    @Column(name = "case_sensitive")
    private Boolean caseSensitive;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public void setQuestion(QuestionEntity question) { this.question = question; }
    public String getAnswerValue() { return answerValue; }
    public void setAnswerValue(String answerValue) { this.answerValue = answerValue; }
    public String getAnswerMediaObjectKey() { return answerMediaObjectKey; }
    public void setAnswerMediaObjectKey(String answerMediaObjectKey) { this.answerMediaObjectKey = answerMediaObjectKey; }
    public String getAnswerMediaContentType() { return answerMediaContentType; }
    public void setAnswerMediaContentType(String answerMediaContentType) { this.answerMediaContentType = answerMediaContentType; }
    public String getAnswerType() { return answerType; }
    public void setAnswerType(String answerType) { this.answerType = answerType; }
    public BigDecimal getToleranceValue() { return toleranceValue; }
    public void setToleranceValue(BigDecimal toleranceValue) { this.toleranceValue = toleranceValue; }
    public Boolean getCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(Boolean caseSensitive) { this.caseSensitive = caseSensitive; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
