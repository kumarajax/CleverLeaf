package com.clearleaf.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record CreateTaxonomyNodeRequest(
        @NotBlank String levelKey,
        UUID parentId,
        @NotBlank
        @Pattern(
                regexp = "[A-Z0-9]+(?:_[A-Z0-9]+)*",
                message = "must contain only uppercase letters, numbers, and single underscores between words")
        String nodeKey,
        @NotBlank String displayName,
        int sortOrder) {
}
