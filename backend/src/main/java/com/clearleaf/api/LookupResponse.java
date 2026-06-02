package com.clearleaf.api;

import java.util.UUID;

public record LookupResponse(
        UUID id,
        String lookupType,
        String lookupCode,
        String lookupMeaning,
        String lookupDescription,
        int sortOrder,
        boolean active) {
}
