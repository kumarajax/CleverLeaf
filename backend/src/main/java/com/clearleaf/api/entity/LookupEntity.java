package com.clearleaf.api.entity;

import com.clearleaf.api.LookupType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "lookup")
public class LookupEntity {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lookup_type", nullable = false, length = 64)
    private LookupType lookupType;

    @Column(name = "lookup_code", nullable = false, length = 64)
    private String lookupCode;

    @Column(name = "lookup_meaning", nullable = false, length = 128)
    private String lookupMeaning;

    @Column(name = "lookup_description", nullable = false, length = 256)
    private String lookupDescription;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LookupEntity() {
    }

    public LookupEntity(UUID id, LookupType lookupType, String lookupCode, String lookupMeaning, String lookupDescription, int sortOrder, boolean active) {
        this.id = id;
        this.tenantId = TenantEntity.DEMO_TENANT_ID;
        this.lookupType = lookupType;
        this.lookupCode = lookupCode;
        this.lookupMeaning = lookupMeaning;
        this.lookupDescription = lookupDescription;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public LookupType getLookupType() {
        return lookupType;
    }

    public void setLookupType(LookupType lookupType) {
        this.lookupType = lookupType;
    }

    public String getLookupCode() {
        return lookupCode;
    }

    public void setLookupCode(String lookupCode) {
        this.lookupCode = lookupCode;
    }

    public String getLookupMeaning() {
        return lookupMeaning;
    }

    public void setLookupMeaning(String lookupMeaning) {
        this.lookupMeaning = lookupMeaning;
    }

    public String getLookupDescription() {
        return lookupDescription;
    }

    public void setLookupDescription(String lookupDescription) {
        this.lookupDescription = lookupDescription;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        LookupEntity that = (LookupEntity) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
