package com.clearleaf.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiGeneratedQuestionResponse(
        UUID id,
        UUID jobId,
        String taxonomyKey,
        String childNodeKey,
        String status,
        String reviewStatus,
        String questionType,
        String difficulty,
        String questionText,
        String explanation,
        String sourceReference,
        List<QuestionOption> options,
        List<String> correctOptionKeys,
        List<String> validationErrors,
        UUID createdQuestionId,
        Instant createdAt,
        Instant updatedAt) {
}
