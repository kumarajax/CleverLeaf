package com.clearleaf.api;

import java.util.List;

public record CsvPreviewResponse(
        String objectKey,
        int totalRows,
        int validRows,
        int invalidRows,
        List<CsvQuestionRowPayload> rows) {
}
