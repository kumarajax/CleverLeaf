package com.clearleaf.api;

public enum WorkflowStatus {
    DRAFT,
    ACTIVE,
    MISSING_ANSWER,
    MISSING_EXPLANATION,
    AI_GENERATED,
    PENDING_REVIEW,
    APPROVED,
    READY_FOR_TEST,
    ARCHIVED,
    REJECTED
}
