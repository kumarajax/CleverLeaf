package com.clearleaf.api;

public record AssignedTestImportRowResponse(
        int lineNumber,
        String testPublicKey,
        String studentSubject,
        String status,
        String message) {
}
