package com.clearleaf.api;

import java.time.Instant;
import java.util.UUID;

public record StudentAssignedTestSummary(
        UUID assignmentId,
        UUID attemptId,
        String testName,
        String status,
        int questionCount,
        int timeAllowedSeconds,
        Instant availableFrom,
        Instant availableUntil,
        Instant assignedAt,
        Instant startedAt,
        Instant submittedAt,
        Integer scorePoints,
        int maxPoints,
        boolean resultsPublished) {
}
