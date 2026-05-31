package com.clearleaf.api;

public record CreateTaxonomyLevelTypeRequest(
        String levelKey,
        String displayName,
        String allowedParentKey,
        int sortOrder) {
}
