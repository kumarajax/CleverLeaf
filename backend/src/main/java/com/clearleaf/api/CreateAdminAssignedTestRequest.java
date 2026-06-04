package com.clearleaf.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateAdminAssignedTestRequest(
        String publicKey,
        String name,
        int timeAllowedSeconds,
        Instant availableFrom,
        Instant availableUntil,
        List<UUID> questionIds) {
}
