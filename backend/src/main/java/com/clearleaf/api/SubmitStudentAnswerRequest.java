package com.clearleaf.api;

import java.util.List;

public record SubmitStudentAnswerRequest(
        List<String> selectedOptionKeys,
        String answerText) {
}
