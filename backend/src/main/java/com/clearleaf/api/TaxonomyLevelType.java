package com.clearleaf.api;

import java.util.UUID;

public record TaxonomyLevelType(
        UUID id,
        String levelKey,
        String displayName,
        String allowedParentKey,
        int sortOrder,
        boolean active) {
}
