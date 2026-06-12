package com.clearleaf.api;

public record SignupRequestSubmission(
        String email,
        String displayName,
        String password,
        String legalVersion,
        boolean termsAccepted,
        String accountType,
        String tenantName,
        boolean joinDemoTenant) {
}
