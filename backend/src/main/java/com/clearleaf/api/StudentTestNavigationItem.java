package com.clearleaf.api;

import java.util.UUID;

public record StudentTestNavigationItem(
        UUID attemptQuestionId,
        int questionNumber,
        boolean answered,
        Boolean correct) {
}
