package com.clearleaf.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiGenerationJobRequest(
        UUID taxonomyNodeId,
        String sourceType,
        String sourceObjectKey,
        String sourceFilename,
        String sourceText,
        String topic,
        String instructions,
        int questionCount,
        List<String> allowedQuestionTypes,
        Map<String, Integer> difficultyMix) {
}
