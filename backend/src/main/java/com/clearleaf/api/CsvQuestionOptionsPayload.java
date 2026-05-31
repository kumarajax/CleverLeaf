package com.clearleaf.api;

public record CsvQuestionOptionsPayload(
        String key,
        String text,
        boolean correct) {
}
