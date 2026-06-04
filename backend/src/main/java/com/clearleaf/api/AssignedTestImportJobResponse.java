package com.clearleaf.api;

import java.time.Instant;
import java.util.UUID;

public record AssignedTestImportJobResponse(
        UUID jobId,
        String objectKey,
        String status,
        int totalRows,
        int importedRows,
        int skippedRows,
        int failedRows,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {
}
