package com.clearleaf.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicAuthService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String keycloakBaseUrl;
    private final String keycloakRealm;
    private final String clientId;

    public PublicAuthService(
            ObjectMapper objectMapper,
            @Value("${app.keycloak.base-url}") String keycloakBaseUrl,
            @Value("${app.keycloak.realm}") String keycloakRealm,
            @Value("${app.keycloak.client-id:clearleaf-web}") String clientId) {
        this.objectMapper = objectMapper;
        this.keycloakBaseUrl = trimTrailingSlash(keycloakBaseUrl);
        this.keycloakRealm = keycloakRealm;
        this.clientId = clientId;
    }

    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request == null ? null : request.email());
        String password = request == null ? null : request.password();
        if (email == null || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        return token(Map.of(
                "grant_type", "password",
                "client_id", clientId,
                "username", email,
                "password", password), email, "Signed in successfully.");
    }

    public LoginResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request == null ? null : request.refreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token is required");
        }
        return token(Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "refresh_token", refreshToken), null, "Session refreshed.");
    }

    private LoginResponse token(Map<String, String> form, String fallbackEmail, String message) {
        try {
            String body = form.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(Collectors.joining("&"));
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            JsonNode payload = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String description = payload.path("error_description").asText("Invalid email or password");
                HttpStatus status = response.statusCode() == 401 ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_GATEWAY;
                throw new ResponseStatusException(status, description);
            }
            return new LoginResponse(
                    "SIGNED_IN",
                    message,
                    payload.path("email").asText(fallbackEmail == null ? "" : fallbackEmail),
                    payload.path("access_token").asText(),
                    payload.path("refresh_token").asText(""),
                    payload.path("token_type").asText("Bearer"),
                    payload.path("expires_in").asLong(0));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Keycloak", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Keycloak", ex);
        }
    }

    private String normalizeEmail(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
