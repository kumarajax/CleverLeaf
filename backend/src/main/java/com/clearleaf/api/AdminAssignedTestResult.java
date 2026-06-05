package com.clearleaf.api;

import java.time.Instant;
import java.util.UUID;

public record AdminAssignedTestResult(
        UUID assignmentId,
        UUID attemptId,
        String studentSubject,
        String status,
        Instant assignedAt,
        Instant startedAt,
        Instant submittedAt,
        Instant resultsPublishedAt,
        Integer scorePoints,
        int maxPoints,
        StudentTestAttemptResponse attempt) {
}
