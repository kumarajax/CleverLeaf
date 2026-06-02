package com.clearleaf.api;

import java.math.BigDecimal;

public record QuestionAnswer(
        String answerValue,
        String answerType,
        BigDecimal toleranceValue,
        Boolean caseSensitive) {
}
