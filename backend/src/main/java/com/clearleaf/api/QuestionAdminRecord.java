package com.clearleaf.api;

import java.util.List;
import java.util.UUID;

public record QuestionAdminRecord(
        UUID id,
        UUID taxonomyNodeId,
        String taxonomyNodeLabel,
        String taxonomyNodeStatus,
        String questionType,
        String difficulty,
        String workflowStatus,
        String questionText,
        String explanation,
        String sourceReference,
        String licenseCategory,
        List<QuestionOption> options) {
}
