package com.clearleaf.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminAssignedTestDetail(
        UUID testId,
        UUID versionId,
        String publicKey,
        String name,
        String status,
        int timeAllowedSeconds,
        Instant availableFrom,
        Instant availableUntil,
        Instant resultsPublishedAt,
        List<QuestionAdminRecord> questions) {
}
