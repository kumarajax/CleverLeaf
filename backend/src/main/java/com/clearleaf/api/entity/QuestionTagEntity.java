package com.clearleaf.api.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_tag")
public class QuestionTagEntity {
    @EmbeddedId
    private QuestionTagId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("questionId")
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    public void setId(QuestionTagId id) { this.id = id; }
    public QuestionTagId getId() { return id; }
    public void setQuestion(QuestionEntity question) { this.question = question; }
}
