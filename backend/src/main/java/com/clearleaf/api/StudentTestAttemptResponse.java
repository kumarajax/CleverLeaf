package com.clearleaf.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudentTestAttemptResponse(
        UUID attemptId,
        String testName,
        String difficulty,
        String status,
        Instant startedAt,
        Instant expiresAt,
        Instant submittedAt,
        int questionCount,
        Integer scorePoints,
        int maxPoints,
        List<StudentTestNavigationItem> navigation,
        StudentTestQuestion currentQuestion,
        List<StudentTestQuestion> questions) {
}
