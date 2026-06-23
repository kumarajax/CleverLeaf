package com.clearleaf.api;

import java.time.Instant;

public record AiConnectionResponse(
        String provider,
        String model,
        String maskedApiKey,
        String status,
        Instant lastVerifiedAt,
        String lastError,
        boolean configured,
        boolean fallbackConfigured) {
}
