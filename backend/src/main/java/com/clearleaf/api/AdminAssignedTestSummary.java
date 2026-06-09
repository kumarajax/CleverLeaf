package com.clearleaf.api;

import java.time.Instant;
import java.util.UUID;

public record AdminAssignedTestSummary(
        UUID testId,
        UUID versionId,
        String publicKey,
        String name,
        String status,
        int questionCount,
        int timeAllowedSeconds,
        Instant availableFrom,
        Instant availableUntil,
        Instant resultsPublishedAt,
        long assignedCount,
        long submittedCount,
        Instant createdAt) {
}
