package com.clearleaf.api;

public record StoredMedia(
        byte[] bytes,
        String contentType) {
}
