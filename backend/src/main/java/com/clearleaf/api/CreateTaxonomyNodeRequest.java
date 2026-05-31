package com.clearleaf.api;

import java.util.UUID;

public record CreateTaxonomyNodeRequest(
        String levelKey,
        UUID parentId,
        String nodeKey,
        String displayName,
        int sortOrder) {
}
