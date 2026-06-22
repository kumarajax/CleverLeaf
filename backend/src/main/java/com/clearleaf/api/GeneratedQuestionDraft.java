package com.clearleaf.api;

import java.util.List;

public record GeneratedQuestionDraft(
        String taxonomyKey,
        String childNodeKey,
        String questionType,
        String difficulty,
        String questionText,
        List<QuestionOption> options,
        List<String> correctOptionKeys,
        String explanation,
        String sourceReference) {
}
