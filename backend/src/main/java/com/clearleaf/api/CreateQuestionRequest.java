package com.clearleaf.api;

import java.util.UUID;

public record CreateQuestionRequest(
        UUID taxonomyNodeId,
        String actor,
        QuestionDraft question) {
}
