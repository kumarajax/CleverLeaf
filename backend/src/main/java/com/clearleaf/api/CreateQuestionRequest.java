package com.clearleaf.api;

import java.util.List;
import java.util.UUID;

public record CreateQuestionRequest(
        UUID taxonomyNodeId,
        String actor,
        QuestionDraft question,
        List<QuestionTaxonomyAssignment> taxonomyAssignments,
        List<QuestionAnswer> answers,
        List<String> tags) {

    public CreateQuestionRequest(UUID taxonomyNodeId, String actor, QuestionDraft question) {
        this(taxonomyNodeId, actor, question, null, null, null);
    }
}
