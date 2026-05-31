package com.clearleaf.api;

import java.util.List;
import java.util.UUID;

public record CsvQuestionRowPayload(
        int lineNumber,
        UUID taxonomyNodeId,
        String actor,
        QuestionType type,
        Difficulty difficulty,
        WorkflowStatus workflowStatus,
        String questionText,
        String explanation,
        String sourceReference,
        String licenseCategory,
        List<CsvQuestionOptionsPayload> options,
        List<String> errors,
        boolean valid) {
}
