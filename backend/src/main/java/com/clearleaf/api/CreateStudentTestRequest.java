package com.clearleaf.api;

import java.util.UUID;

public record CreateStudentTestRequest(
        UUID taxonomyNodeId,
        String difficulty,
        int questionCount,
        String testName,
        Integer secondsPerQuestion) {
}
