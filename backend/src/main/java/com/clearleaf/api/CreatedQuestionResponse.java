package com.clearleaf.api;

import java.util.UUID;

public record CreatedQuestionResponse(UUID id, WorkflowStatus workflowStatus) {
}
