package com.clearleaf.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiGenerationJobResponse(
        UUID id,
        UUID taxonomyNodeId,
        String taxonomyKey,
        String childNodeKey,
        String taxonomyPath,
        String sourceType,
        String sourceObjectKey,
        String sourceFilename,
        String topic,
        String instructions,
        int questionCount,
        String status,
        String errorMessage,
        int chunkCount,
        int generatedCount,
        int validCount,
        int approvedCount,
        Instant createdAt,
        Instant updatedAt,
        List<AiGeneratedQuestionResponse> questions) {
}
