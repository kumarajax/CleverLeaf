package com.clearleaf.api;

public record BulkImportColumn(
        String name,
        boolean required,
        String description) {
}
