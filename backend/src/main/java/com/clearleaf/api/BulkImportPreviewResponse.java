package com.clearleaf.api;

import java.util.List;

public record BulkImportPreviewResponse(
        String objectKey,
        String stepCode,
        int totalRows,
        int validRows,
        int invalidRows,
        List<BulkImportRowResult> rows) {
}
