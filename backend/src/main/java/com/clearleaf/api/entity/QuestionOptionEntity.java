package com.clearleaf.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "question_option")
public class QuestionOptionEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    @Column(name = "option_key", nullable = false, length = 16)
    private String optionKey;

    @Column(name = "option_text")
    private String optionText;

    @Column(name = "option_media_object_key")
    private String optionMediaObjectKey;

    @Column(name = "option_media_content_type", length = 128)
    private String optionMediaContentType;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public void setQuestion(QuestionEntity question) { this.question = question; }
    public String getOptionKey() { return optionKey; }
    public void setOptionKey(String optionKey) { this.optionKey = optionKey; }
    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }
    public String getOptionMediaObjectKey() { return optionMediaObjectKey; }
    public void setOptionMediaObjectKey(String optionMediaObjectKey) { this.optionMediaObjectKey = optionMediaObjectKey; }
    public String getOptionMediaContentType() { return optionMediaContentType; }
    public void setOptionMediaContentType(String optionMediaContentType) { this.optionMediaContentType = optionMediaContentType; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
