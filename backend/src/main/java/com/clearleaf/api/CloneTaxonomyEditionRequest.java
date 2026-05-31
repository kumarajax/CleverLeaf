package com.clearleaf.api;

import java.util.UUID;

public record CloneTaxonomyEditionRequest(
        UUID sourceEditionId,
        String clonedEditionKey,
        String clonedEditionDisplayName) {
}
