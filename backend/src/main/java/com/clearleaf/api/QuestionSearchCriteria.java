package com.clearleaf.api;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public record QuestionSearchCriteria(
        String questionType,
        String difficulty,
        String workflowStatus,
        UUID taxonomyNodeId,
        boolean includeDescendants,
        UUID curriculumId,
        UUID editionId,
        UUID gradeId,
        UUID subjectId,
        UUID chapterId,
        UUID topicId,
        String search) {

    public List<UUID> pedigreeNodeIds() {
        return Stream.of(curriculumId, editionId, gradeId, subjectId, chapterId, topicId)
                .filter(value -> value != null)
                .toList();
    }
}
