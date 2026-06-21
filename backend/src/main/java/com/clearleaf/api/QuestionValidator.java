package com.clearleaf.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuestionValidator {
    public List<String> validate(QuestionDraft question) {
        return validate(question, false);
    }

    public List<String> validate(QuestionDraft question, boolean allowIncomplete) {
        List<String> errors = new ArrayList<>();
        if (question == null) {
            return List.of("Question is required");
        }
        if (question.type() == null) {
            errors.add("Question type is required");
        }
        if (question.difficulty() == null) {
            errors.add("Difficulty is required");
        }
        if (question.workflowStatus() == null) {
            errors.add("Workflow status is required");
        }
        if (isBlank(question.questionText()) && isBlank(question.questionMediaObjectKey())) {
            errors.add("Question text or image is required");
        }
        if (question.workflowStatus() == WorkflowStatus.READY_FOR_TEST) {
            if (question.explanation() == null || question.explanation().isBlank()) {
                errors.add("Ready-for-test questions require an explanation");
            }
            if (question.sourceReference() == null || question.sourceReference().isBlank()) {
                errors.add("Ready-for-test questions require provenance");
            }
            if (question.licenseCategory() == null || question.licenseCategory().isBlank()) {
                errors.add("Ready-for-test questions require a license category");
            }
        }
        validateOptions(question, errors, allowIncomplete);
        return errors;
    }

    private void validateOptions(QuestionDraft question, List<String> errors, boolean allowIncomplete) {
        if (question.type() == null) {
            return;
        }
        if (question.type() != QuestionType.SINGLE_SELECT
                && question.type() != QuestionType.MULTIPLE_SELECT
                && question.type() != QuestionType.TRUE_FALSE) {
            return;
        }
        List<QuestionOption> options = question.options() == null ? List.of() : question.options();
        if (options.isEmpty() && (allowIncomplete
                || question.workflowStatus() == WorkflowStatus.DRAFT
                || question.workflowStatus() == WorkflowStatus.MISSING_ANSWER)) {
            return;
        }
        if (options.stream().anyMatch(option -> option == null
                || (isBlank(option.text()) && isBlank(option.mediaObjectKey())))) {
            errors.add("Option text or image is required");
            return;
        }
        long correctCount = options.stream().filter(QuestionOption::correct).count();
        Set<String> keys = new HashSet<>();
        if (options.stream().anyMatch(option -> option.key() == null || !keys.add(option.key()))) {
            errors.add("Option keys must be present and unique");
        }
        if (allowIncomplete) {
            return;
        }
        if (question.type() == QuestionType.SINGLE_SELECT && correctCount != 1) {
            errors.add("Single-select questions require exactly one correct option");
        }
        if (question.type() == QuestionType.MULTIPLE_SELECT
                && (correctCount < 2 || correctCount == options.size())) {
            errors.add("Multiple-select questions require at least two correct options and one incorrect option");
        }
        if (question.type() == QuestionType.TRUE_FALSE
                && (options.size() != 2 || correctCount != 1)) {
            errors.add("True/false questions require exactly two options and one correct option");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
