package com.clearleaf.api;

import java.util.List;
import java.util.UUID;

public record StudentTestQuestion(
        UUID attemptQuestionId,
        int questionNumber,
        String questionType,
        String questionText,
        String questionMediaObjectKey,
        String questionMediaContentType,
        List<StudentQuestionOption> options,
        List<String> selectedOptionKeys,
        String answerText,
        List<String> correctOptionKeys,
        String correctAnswerText,
        String correctAnswerMediaObjectKey,
        String correctAnswerMediaContentType,
        Boolean correct) {
}
