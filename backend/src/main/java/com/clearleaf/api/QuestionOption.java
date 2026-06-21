package com.clearleaf.api;

public record QuestionOption(
        String key,
        String text,
        String mediaObjectKey,
        String mediaContentType,
        boolean correct) {
}
