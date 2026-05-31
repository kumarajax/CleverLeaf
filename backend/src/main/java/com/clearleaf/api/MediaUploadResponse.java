package com.clearleaf.api;

public record MediaUploadResponse(
        String bucket,
        String objectKey,
        String originalFilename,
        String contentType,
        long size,
        String etag) {
}
