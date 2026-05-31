package com.clearleaf.api;

import java.util.UUID;

public record TaxonomyCloneResponse(
        UUID editionId,
        UUID curriculumId,
        String editionKey,
        String editionDisplayName,
        String status) {
}
