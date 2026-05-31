package com.clearleaf.api;

import java.util.UUID;

public record TaxonomyNode(
        UUID id,
        UUID levelTypeId,
        UUID parentId,
        String nodeKey,
        String displayName,
        String status,
        int sortOrder) {
}
