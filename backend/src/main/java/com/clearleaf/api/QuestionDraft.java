package com.clearleaf.api;

import java.util.List;

public record QuestionDraft(
        QuestionType type,
        Difficulty difficulty,
        WorkflowStatus workflowStatus,
        String questionText,
        String questionMediaObjectKey,
        String questionMediaContentType,
        String explanation,
        String sourceReference,
        String licenseCategory,
        List<QuestionOption> options) {
}
