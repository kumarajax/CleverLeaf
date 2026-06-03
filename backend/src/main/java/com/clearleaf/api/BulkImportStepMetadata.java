package com.clearleaf.api;

import java.util.List;

public record BulkImportStepMetadata(
        int sequence,
        String stepCode,
        String label,
        List<BulkImportColumn> columns) {
}
