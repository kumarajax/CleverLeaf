package com.clearleaf.api;

import java.util.List;
import java.util.Map;

public record QuestionGenerationRequest(
        String taxonomyKey,
        String childNodeKey,
        String taxonomyPath,
        String topic,
        String instructions,
        int requestedQuestionCount,
        List<String> allowedQuestionTypes,
        Map<String, Integer> difficultyMix,
        String sourceReference,
        String chunkText) {
}
