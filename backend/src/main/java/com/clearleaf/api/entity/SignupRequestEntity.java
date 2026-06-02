package com.clearleaf.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "signup_requests")
public class SignupRequestEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "encrypted_password")
    private String encryptedPassword;

    @Column(name = "password_nonce")
    private String passwordNonce;

    @Column(nullable = false)
    private String status;

    @Column(name = "legal_version", nullable = false)
    private String legalVersion;

    @Column(name = "terms_accepted_at", nullable = false)
    private OffsetDateTime termsAcceptedAt;

    @Column(name = "requester_ip")
    private String requesterIp;

    @Column(name = "requester_user_agent")
    private String requesterUserAgent;

    @Column(name = "approve_token_hash", nullable = false)
    private String approveTokenHash;

    @Column(name = "reject_token_hash", nullable = false)
    private String rejectTokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "reviewed_action")
    private String reviewedAction;

    @Column(name = "review_reason")
    private String reviewReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public String getPasswordNonce() {
        return passwordNonce;
    }

    public void setPasswordNonce(String passwordNonce) {
        this.passwordNonce = passwordNonce;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLegalVersion() {
        return legalVersion;
    }

    public void setLegalVersion(String legalVersion) {
        this.legalVersion = legalVersion;
    }

    public OffsetDateTime getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(OffsetDateTime termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public String getRequesterIp() {
        return requesterIp;
    }

    public void setRequesterIp(String requesterIp) {
        this.requesterIp = requesterIp;
    }

    public String getRequesterUserAgent() {
        return requesterUserAgent;
    }

    public void setRequesterUserAgent(String requesterUserAgent) {
        this.requesterUserAgent = requesterUserAgent;
    }

    public String getApproveTokenHash() {
        return approveTokenHash;
    }

    public void setApproveTokenHash(String approveTokenHash) {
        this.approveTokenHash = approveTokenHash;
    }

    public String getRejectTokenHash() {
        return rejectTokenHash;
    }

    public void setRejectTokenHash(String rejectTokenHash) {
        this.rejectTokenHash = rejectTokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewedAction() {
        return reviewedAction;
    }

    public void setReviewedAction(String reviewedAction) {
        this.reviewedAction = reviewedAction;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }
}
