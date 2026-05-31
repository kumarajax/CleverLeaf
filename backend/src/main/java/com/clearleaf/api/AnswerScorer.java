package com.clearleaf.api;

import java.util.Set;
import java.util.stream.Collectors;

public class AnswerScorer {
    public boolean exactMatch(QuestionDraft question, Set<String> submittedOptionKeys) {
        Set<String> expected = question.options().stream()
                .filter(QuestionOption::correct)
                .map(QuestionOption::key)
                .collect(Collectors.toSet());
        return expected.equals(submittedOptionKeys);
    }
}
