package com.clearleaf.api;

import java.util.List;

public record CsvImportSummary(
        String objectKey,
        int importedRows,
        int failedRows,
        List<CsvQuestionRowPayload> rows) {
}
