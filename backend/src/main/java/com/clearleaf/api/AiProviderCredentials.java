package com.clearleaf.api;

public record AiProviderCredentials(String provider, String model, String apiKey, String baseUrl) {
}
