package com.clearleaf.api;

import java.util.List;

public record UpdateAiGeneratedQuestionRequest(
        String questionType,
        String difficulty,
        String questionText,
        String explanation,
        String sourceReference,
        List<QuestionOption> options,
        List<String> correctOptionKeys) {
}
