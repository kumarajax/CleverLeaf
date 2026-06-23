package com.clearleaf.api;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiProviderConnectionService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PROVIDER_OPENAI = "OPENAI";
    private static final String STATUS_VERIFIED = "VERIFIED";

    private final JdbcTemplate jdbcTemplate;
    private final String encryptionKeyMaterial;
    private final String fallbackApiKey;
    private final String fallbackModel;
    private final String openAiBaseUrl;

    public AiProviderConnectionService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.ai.connection-encryption-key}") String encryptionKeyMaterial,
            @Value("${app.ai.openai.api-key}") String fallbackApiKey,
            @Value("${app.ai.model}") String fallbackModel,
            @Value("${app.ai.openai.base-url}") String openAiBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionKeyMaterial = encryptionKeyMaterial;
        this.fallbackApiKey = fallbackApiKey == null ? "" : fallbackApiKey.trim();
        this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() ? "gpt-5.5" : fallbackModel.trim();
        this.openAiBaseUrl = openAiBaseUrl;
    }

    @Transactional(readOnly = true)
    public AiConnectionResponse getConnection(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT provider, model, masked_api_key, status, last_verified_at, last_error
                FROM ai_provider_connection
                WHERE tenant_id = ? AND provider = ?
                """, (rs, rowNum) -> toResponse(rs), tenantId, PROVIDER_OPENAI)
                .stream()
                .findFirst()
                .orElse(new AiConnectionResponse(PROVIDER_OPENAI, fallbackModel, null, "NOT_CONFIGURED", null, null, false, fallbackConfigured()));
    }

    public AiConnectionResponse verifyConnection(AiConnectionRequest request) {
        String provider = normalizeProvider(request == null ? null : request.provider());
        String model = normalizeModel(request == null ? null : request.model());
        String apiKey = requireApiKey(request == null ? null : request.apiKey());
        verifyOpenAi(model, apiKey);
        return new AiConnectionResponse(provider, model, mask(apiKey), STATUS_VERIFIED, Instant.now(), null, true, fallbackConfigured());
    }

    @Transactional
    public AiConnectionResponse saveConnection(UUID tenantId, AiConnectionRequest request) {
        String provider = normalizeProvider(request == null ? null : request.provider());
        String model = normalizeModel(request == null ? null : request.model());
        String apiKey = requireApiKey(request == null ? null : request.apiKey());
        verifyOpenAi(model, apiKey);
        EncryptedSecret encrypted = encrypt(apiKey);
        String masked = mask(apiKey);
        jdbcTemplate.update("""
                INSERT INTO ai_provider_connection
                    (id, tenant_id, provider, model, encrypted_api_key, api_key_nonce, masked_api_key, status, last_verified_at, last_error)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), NULL)
                ON CONFLICT (tenant_id, provider)
                DO UPDATE SET model = EXCLUDED.model,
                    encrypted_api_key = EXCLUDED.encrypted_api_key,
                    api_key_nonce = EXCLUDED.api_key_nonce,
                    masked_api_key = EXCLUDED.masked_api_key,
                    status = EXCLUDED.status,
                    last_verified_at = EXCLUDED.last_verified_at,
                    last_error = NULL,
                    updated_at = now()
                """, UUID.randomUUID(), tenantId, provider, model, encrypted.cipherText(), encrypted.nonce(), masked, STATUS_VERIFIED);
        return getConnection(tenantId);
    }

    @Transactional(readOnly = true)
    public AiProviderCredentials resolveCredentials(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT provider, model, encrypted_api_key, api_key_nonce
                FROM ai_provider_connection
                WHERE tenant_id = ? AND provider = ? AND status = ?
                """, (rs, rowNum) -> new AiProviderCredentials(
                        rs.getString("provider"),
                        rs.getString("model"),
                        decrypt(rs.getString("encrypted_api_key"), rs.getString("api_key_nonce")),
                        baseUrl(rs.getString("provider"))),
                tenantId, PROVIDER_OPENAI, STATUS_VERIFIED)
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    if (fallbackApiKey.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI provider connection is not configured");
                    }
                    return new AiProviderCredentials(PROVIDER_OPENAI, fallbackModel, fallbackApiKey, openAiBaseUrl);
                });
    }

    private AiConnectionResponse toResponse(ResultSet rs) throws java.sql.SQLException {
        Timestamp lastVerified = rs.getTimestamp("last_verified_at");
        return new AiConnectionResponse(
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("masked_api_key"),
                rs.getString("status"),
                lastVerified == null ? null : lastVerified.toInstant(),
                rs.getString("last_error"),
                true,
                fallbackConfigured());
    }

    private void verifyOpenAi(String model, String apiKey) {
        try {
            RestClient.builder()
                    .baseUrl(openAiBaseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .build()
                    .get()
                    .uri("/models/{model}", model)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to verify OpenAI connection: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to verify OpenAI connection", ex);
        }
    }

    private String normalizeProvider(String provider) {
        String normalized = provider == null || provider.isBlank() ? PROVIDER_OPENAI : provider.trim().toUpperCase(Locale.ROOT);
        if (!PROVIDER_OPENAI.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only OpenAI is supported currently");
        }
        return normalized;
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) return fallbackModel;
        return model.trim();
    }

    private String requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API key is required");
        }
        return apiKey.trim();
    }

    private String baseUrl(String provider) {
        return PROVIDER_OPENAI.equals(provider) ? openAiBaseUrl : openAiBaseUrl;
    }

    private boolean fallbackConfigured() {
        return !fallbackApiKey.isBlank();
    }

    private String mask(String apiKey) {
        if (apiKey.length() <= 8) return "..." + apiKey.substring(Math.max(0, apiKey.length() - 4));
        return apiKey.substring(0, Math.min(3, apiKey.length())) + "-..." + apiKey.substring(apiKey.length() - 4);
    }

    private EncryptedSecret encrypt(String value) {
        try {
            byte[] nonce = new byte[12];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, nonce));
            return new EncryptedSecret(base64(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8))), base64(nonce));
        } catch (GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to protect AI provider key", ex);
        }
    }

    private String decrypt(String cipherText, String nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, Base64.getDecoder().decode(nonce)));
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read AI provider key", ex);
        }
    }

    private SecretKeySpec encryptionKey() {
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptionKeyMaterial.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to build AI provider key encryption key", ex);
        }
    }

    private String base64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private record EncryptedSecret(String cipherText, String nonce) {
    }
}
