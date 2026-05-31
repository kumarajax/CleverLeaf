package com.clearleaf.api;

import java.util.UUID;

public record UpdateTaxonomyNodeRequest(
        String levelKey,
        UUID parentId,
        String nodeKey,
        String displayName,
        int sortOrder,
        String status) {
}
