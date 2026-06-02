package com.clearleaf.api;

import com.clearleaf.api.entity.SignupRequestEntity;
import com.clearleaf.api.repository.SignupRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SignupApprovalService {
    private static final Logger log = LoggerFactory.getLogger(SignupApprovalService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SignupRequestRepository signupRequests;
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String keycloakBaseUrl;
    private final String keycloakRealm;
    private final String keycloakAdminUser;
    private final String keycloakAdminPassword;
    private final String publicBaseUrl;
    private final boolean approvalRequired;
    private final String approvalEmailTo;
    private final String legalVersion;
    private final String encryptionKeyMaterial;
    private final int tokenDays;
    private final String mailFrom;

    public SignupApprovalService(
            SignupRequestRepository signupRequests,
            ObjectMapper objectMapper,
            JavaMailSender mailSender,
            @Value("${app.keycloak.base-url}") String keycloakBaseUrl,
            @Value("${app.keycloak.realm}") String keycloakRealm,
            @Value("${app.keycloak.admin-user}") String keycloakAdminUser,
            @Value("${app.keycloak.admin-password}") String keycloakAdminPassword,
            @Value("${app.public-base-url}") String publicBaseUrl,
            @Value("${app.signup.approval-required:Y}") String approvalRequired,
            @Value("${app.signup.approval-email-to}") String approvalEmailTo,
            @Value("${app.legal.current-version}") String legalVersion,
            @Value("${app.signup.password-encryption-key}") String encryptionKeyMaterial,
            @Value("${app.signup.token-days:7}") int tokenDays,
            @Value("${spring.mail.username:}") String mailFrom) {
        this.signupRequests = signupRequests;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.keycloakBaseUrl = trimTrailingSlash(keycloakBaseUrl);
        this.keycloakRealm = keycloakRealm;
        this.keycloakAdminUser = keycloakAdminUser;
        this.keycloakAdminPassword = keycloakAdminPassword;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.approvalRequired = isTruthy(approvalRequired);
        this.approvalEmailTo = approvalEmailTo;
        this.legalVersion = legalVersion;
        this.encryptionKeyMaterial = encryptionKeyMaterial;
        this.tokenDays = tokenDays;
        this.mailFrom = mailFrom;
    }

    @Transactional
    public SignupRequestStatusRecord submit(SignupRequestSubmission submission, String ipAddress, String userAgent) {
        String email = normalizeEmail(submission == null ? null : submission.email());
        String displayName = trimToNull(submission == null ? null : submission.displayName());
        String password = submission == null ? null : submission.password();
        if (email == null || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        if (submission == null || !submission.termsAccepted() || !legalVersion.equals(submission.legalVersion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You must accept the current terms");
        }
        if (keycloakUserExists(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email");
        }
        if (!approvalRequired) {
            createKeycloakStudent(email, displayName, password);
            sendApplicantCreatedEmail(email);
            return status("CREATED", "Account created successfully. You can now sign in.", email, displayName);
        }
        if (signupRequests.existsByEmailIgnoreCaseAndStatus(email, "PENDING")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A signup request is already pending for this email");
        }
        String approveToken = randomToken();
        String rejectToken = randomToken();
        EncryptedPassword encryptedPassword = encryptPassword(password);
        OffsetDateTime now = OffsetDateTime.now();
        SignupRequestEntity request = new SignupRequestEntity();
        request.setId(UUID.randomUUID());
        request.setEmail(email);
        request.setDisplayName(displayName);
        request.setEncryptedPassword(encryptedPassword.cipherText());
        request.setPasswordNonce(encryptedPassword.nonce());
        request.setStatus("PENDING");
        request.setLegalVersion(legalVersion);
        request.setTermsAcceptedAt(now);
        request.setRequesterIp(trimToNull(ipAddress));
        request.setRequesterUserAgent(trimToNull(userAgent));
        request.setApproveTokenHash(hashToken(approveToken));
        request.setRejectTokenHash(hashToken(rejectToken));
        request.setExpiresAt(now.plusDays(Math.max(1, tokenDays)));
        signupRequests.save(request);
        sendAdminEmail(email, displayName, approveToken, rejectToken);
        sendApplicantReceivedEmail(email);
        return status("PENDING", "Signup request sent for approval.", email, displayName);
    }

    public SignupRequestStatusRecord tokenInfo(String token) {
        SignupRequestEntity request = findByAnyToken(token);
        String effectiveStatus = effectiveStatus(request);
        return status(effectiveStatus, statusMessage(effectiveStatus), request.getEmail(), request.getDisplayName());
    }

    @Transactional
    public SignupRequestStatusRecord approve(String token) {
        SignupRequestEntity request = findPendingByToken(token, true);
        createKeycloakStudent(request.getEmail(), request.getDisplayName(),
                decryptPassword(request.getEncryptedPassword(), request.getPasswordNonce()));
        request.setStatus("APPROVED");
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedAction("APPROVED");
        request.setEncryptedPassword(null);
        request.setPasswordNonce(null);
        signupRequests.save(request);
        sendApplicantApprovedEmail(request.getEmail());
        return status("APPROVED", "Signup request approved. ClearLeaf account created.",
                request.getEmail(), request.getDisplayName());
    }

    @Transactional
    public SignupRequestStatusRecord reject(String token, RejectSignupRequest rejection) {
        SignupRequestEntity request = findPendingByToken(token, false);
        String reason = trimToNull(rejection == null ? null : rejection.reason());
        request.setStatus("REJECTED");
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedAction("REJECTED");
        request.setReviewReason(reason);
        request.setEncryptedPassword(null);
        request.setPasswordNonce(null);
        signupRequests.save(request);
        sendApplicantRejectedEmail(request.getEmail(), reason);
        return status("REJECTED", "Signup request rejected.", request.getEmail(), request.getDisplayName());
    }

    private SignupRequestEntity findByAnyToken(String token) {
        String hash = hashToken(requiredToken(token));
        return signupRequests.findFirstByApproveTokenHashOrRejectTokenHash(hash, hash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Signup approval link was not found"));
    }

    private SignupRequestEntity findPendingByToken(String token, boolean approve) {
        String hash = hashToken(requiredToken(token));
        SignupRequestEntity request = (approve
                ? signupRequests.findFirstByApproveTokenHashAndStatus(hash, "PENDING")
                : signupRequests.findFirstByRejectTokenHashAndStatus(hash, "PENDING"))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Signup approval link was not found or already used"));
        if (request.getExpiresAt().isBefore(OffsetDateTime.now())) {
            request.setStatus("EXPIRED");
            signupRequests.save(request);
            throw new ResponseStatusException(HttpStatus.GONE, "Signup approval link has expired");
        }
        return request;
    }

    private void createKeycloakStudent(String email, String displayName, String password) {
        String adminToken = adminAccessToken();
        try {
            String fallbackName = email.substring(0, email.indexOf('@'));
            Map<String, Object> body = Map.of(
                    "username", email,
                    "email", email,
                    "firstName", displayName == null ? fallbackName : displayName,
                    "lastName", "ClearLeaf",
                    "enabled", true,
                    "emailVerified", true,
                    "credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)));
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users"))
                    .header("Authorization", "Bearer " + adminToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create Keycloak user");
            }
            assignStudentRole(userId(response, email, adminToken), adminToken);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create Keycloak user", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create Keycloak user", ex);
        }
    }

    private String userId(HttpResponse<String> response, String email, String adminToken) throws IOException, InterruptedException {
        String location = response.headers().firstValue("Location").orElse("");
        if (location.contains("/users/")) {
            return location.substring(location.lastIndexOf('/') + 1);
        }
        JsonNode users = objectMapper.readTree(sendAdminGet("/admin/realms/" + keycloakRealm + "/users?email=" + urlEncode(email) + "&exact=true", adminToken).body());
        if (users.isArray() && !users.isEmpty()) {
            return users.get(0).get("id").asText();
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to resolve created Keycloak user");
    }

    private void assignStudentRole(String userId, String adminToken) throws IOException, InterruptedException {
        HttpResponse<String> roleResponse = sendAdminGet("/admin/realms/" + keycloakRealm + "/roles/student", adminToken);
        if (roleResponse.statusCode() < 200 || roleResponse.statusCode() >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to find Keycloak student role");
        }
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(keycloakBaseUrl + "/admin/realms/" + keycloakRealm + "/users/" + userId + "/role-mappings/realm"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("[" + roleResponse.body() + "]")).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to assign Keycloak student role");
        }
    }

    private boolean keycloakUserExists(String email) {
        String token = adminAccessToken();
        try {
            HttpResponse<String> response = sendAdminGet("/admin/realms/" + keycloakRealm + "/users?email=" + urlEncode(email) + "&exact=true", token);
            return response.statusCode() >= 200 && response.statusCode() < 300
                    && objectMapper.readTree(response.body()).size() > 0;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to check existing Keycloak users", ex);
        }
    }

    private HttpResponse<String> sendAdminGet(String path, String token) throws IOException, InterruptedException {
        return httpClient.send(HttpRequest.newBuilder().uri(URI.create(keycloakBaseUrl + path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String adminAccessToken() {
        try {
            String body = Map.of("grant_type", "password", "client_id", "admin-cli",
                            "username", keycloakAdminUser, "password", keycloakAdminPassword)
                    .entrySet().stream().map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                    .collect(Collectors.joining("&"));
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(keycloakBaseUrl + "/realms/master/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            JsonNode token = objectMapper.readTree(response.body()).get("access_token");
            if (response.statusCode() < 200 || response.statusCode() >= 300 || token == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to authenticate Keycloak admin");
            }
            return token.asText();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Keycloak", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Keycloak", ex);
        }
    }

    private void sendAdminEmail(String email, String displayName, String approveToken, String rejectToken) {
        String name = displayName == null ? "" : "\nName: " + displayName;
        sendEmail(approvalEmailTo, "ClearLeaf signup approval request",
                "A new ClearLeaf account request is pending review.\n\nEmail: " + email + name
                        + "\n\nApprove:\n" + publicBaseUrl + "/signup-approvals/" + approveToken + "/approve"
                        + "\n\nReject:\n" + publicBaseUrl + "/signup-approvals/" + rejectToken + "/reject");
    }

    private void sendApplicantReceivedEmail(String email) {
        sendEmail(email, "ClearLeaf signup request received", "Your ClearLeaf account request is pending approval.");
    }

    private void sendApplicantApprovedEmail(String email) {
        sendEmail(email, "ClearLeaf signup approved", "Your ClearLeaf account was approved. Sign in at " + publicBaseUrl + "/account.");
    }

    private void sendApplicantCreatedEmail(String email) {
        sendEmail(email, "ClearLeaf account created", "Your ClearLeaf account was created. Sign in at " + publicBaseUrl + "/account.");
    }

    private void sendApplicantRejectedEmail(String email, String reason) {
        sendEmail(email, "ClearLeaf signup request rejected", "Your ClearLeaf account request was not approved."
                + (reason == null ? "" : "\n\nReason:\n" + reason));
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!mailFrom.isBlank()) message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Unable to send signup email to {}", to, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to send email", ex);
        }
    }

    private EncryptedPassword encryptPassword(String password) {
        try {
            byte[] nonce = new byte[12];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, nonce));
            return new EncryptedPassword(base64(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8))), base64(nonce));
        } catch (GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to protect signup password", ex);
        }
    }

    private String decryptPassword(String cipherText, String nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, Base64.getDecoder().decode(nonce)));
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read signup password", ex);
        }
    }

    private SecretKeySpec encryptionKey() {
        return new SecretKeySpec(sha256(encryptionKeyMaterial), "AES");
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        return HexFormat.of().formatHex(sha256(token));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to hash value", ex);
        }
    }

    private String base64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private SignupRequestStatusRecord status(String status, String message, String email, String displayName) {
        return new SignupRequestStatusRecord(status, message, email, displayName);
    }

    private String effectiveStatus(SignupRequestEntity request) {
        return "PENDING".equals(request.getStatus()) && request.getExpiresAt().isBefore(OffsetDateTime.now())
                ? "EXPIRED"
                : request.getStatus();
    }

    private String statusMessage(String status) {
        return switch (status) {
            case "APPROVED" -> "Signup request was already approved.";
            case "REJECTED" -> "Signup request was already rejected.";
            case "EXPIRED" -> "Signup approval link has expired.";
            default -> "Signup request is pending review.";
        };
    }

    private String requiredToken(String token) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signup approval token is required");
        return token.trim();
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

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean isTruthy(String value) {
        return value != null && (value.equalsIgnoreCase("y") || value.equalsIgnoreCase("yes")
                || value.equalsIgnoreCase("true") || value.equals("1"));
    }

    private record EncryptedPassword(String cipherText, String nonce) {}
}
