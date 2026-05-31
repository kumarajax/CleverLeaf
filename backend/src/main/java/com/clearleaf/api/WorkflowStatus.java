package com.clearleaf.api;

public enum WorkflowStatus {
    DRAFT,
    MISSING_ANSWER,
    MISSING_EXPLANATION,
    AI_GENERATED,
    PENDING_REVIEW,
    APPROVED,
    READY_FOR_TEST,
    ARCHIVED,
    REJECTED
}
