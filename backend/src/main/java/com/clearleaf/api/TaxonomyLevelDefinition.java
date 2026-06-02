package com.clearleaf.api;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public enum TaxonomyLevelDefinition {
    CURRICULUM(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "CURRICULUM",
            null,
            "Curriculum",
            "Top-level curriculum or board container",
            1),
    EDITION(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "EDITION",
            "CURRICULUM",
            "Edition",
            "A version of a curriculum that groups the grade structure",
            2),
    GRADE(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            "GRADE",
            "EDITION",
            "Grade",
            "A grade level inside a curriculum edition",
            3),
    SUBJECT(
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            "SUBJECT",
            "GRADE",
            "Subject",
            "A subject inside a grade",
            4),
    CHAPTER(
            UUID.fromString("00000000-0000-0000-0000-000000000005"),
            "CHAPTER",
            "SUBJECT",
            "Chapter",
            "A chapter inside a subject",
            5),
    TOPIC(
            UUID.fromString("00000000-0000-0000-0000-000000000006"),
            "TOPIC",
            "CHAPTER",
            "Topic",
            "A topic inside a chapter",
            6);

    private final UUID seedId;
    private final String lookupCode;
    private final String allowedParentKey;
    private final String meaning;
    private final String description;
    private final int sortOrder;

    TaxonomyLevelDefinition(UUID seedId, String lookupCode, String allowedParentKey, String meaning, String description, int sortOrder) {
        this.seedId = seedId;
        this.lookupCode = lookupCode;
        this.allowedParentKey = allowedParentKey;
        this.meaning = meaning;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public UUID seedId() {
        return seedId;
    }

    public String lookupCode() {
        return lookupCode;
    }

    public String allowedParentKey() {
        return allowedParentKey;
    }

    public String meaning() {
        return meaning;
    }

    public String description() {
        return description;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public static Optional<TaxonomyLevelDefinition> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(definition -> definition.lookupCode.equals(normalized))
                .findFirst();
    }
}
