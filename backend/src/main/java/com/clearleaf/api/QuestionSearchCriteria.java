package com.clearleaf.api;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public record QuestionSearchCriteria(
        String questionType,
        String difficulty,
        String workflowStatus,
        List<String> workflowStatuses,
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

    public List<String> normalizedWorkflowStatuses() {
        if (workflowStatuses != null && !workflowStatuses.isEmpty()) {
            return workflowStatuses.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toUpperCase())
                    .distinct()
                    .toList();
        }
        return workflowStatus == null || workflowStatus.isBlank() ? List.of() : List.of(workflowStatus.trim().toUpperCase());
    }
}
