package com.clearleaf.api;

import java.util.List;
import java.util.Map;

public record BulkImportRowResult(
        int lineNumber,
        Map<String, String> values,
        List<String> errors,
        boolean valid) {
}
