package com.clearleaf.api;

import java.util.List;

public record BulkImportSummary(
        String objectKey,
        String stepCode,
        int totalRows,
        int importedRows,
        int failedRows,
        List<BulkImportRowResult> rows) {
}
