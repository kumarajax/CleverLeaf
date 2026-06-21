package com.clearleaf.api;

import java.math.BigDecimal;

public record QuestionAnswer(
        String answerValue,
        String answerMediaObjectKey,
        String answerMediaContentType,
        String answerType,
        BigDecimal toleranceValue,
        Boolean caseSensitive) {
}
