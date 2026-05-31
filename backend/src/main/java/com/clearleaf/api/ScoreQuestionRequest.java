package com.clearleaf.api;

import java.util.Set;

public record ScoreQuestionRequest(QuestionDraft question, Set<String> submittedOptionKeys) {
}
