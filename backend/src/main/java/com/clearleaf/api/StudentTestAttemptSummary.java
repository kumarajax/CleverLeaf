package com.clearleaf.api;

import java.time.Instant;
import java.util.UUID;

public record StudentTestAttemptSummary(
        UUID attemptId,
        String testName,
        UUID taxonomyNodeId,
        String taxonomyName,
        String taxonomyPath,
        String difficulty,
        String status,
        Instant startedAt,
        Instant submittedAt,
        Integer scorePoints,
        int maxPoints,
        int questionCount) {
}
