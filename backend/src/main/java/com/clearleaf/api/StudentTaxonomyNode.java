package com.clearleaf.api;

import java.util.UUID;

public record StudentTaxonomyNode(
        UUID id,
        UUID parentId,
        String externalKey,
        String nodeKey,
        String displayName,
        String levelKey,
        String gradeLabel,
        String path,
        long questionCount) {
}
