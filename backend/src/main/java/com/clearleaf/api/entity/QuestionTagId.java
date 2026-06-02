package com.clearleaf.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class QuestionTagId implements Serializable {
    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "tag_code")
    private String tagCode;

    public QuestionTagId() {
    }

    public QuestionTagId(UUID questionId, String tagCode) {
        this.questionId = questionId;
        this.tagCode = tagCode;
    }

    public String getTagCode() {
        return tagCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QuestionTagId that)) return false;
        return Objects.equals(questionId, that.questionId) && Objects.equals(tagCode, that.tagCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, tagCode);
    }
}
