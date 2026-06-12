package com.clearleaf.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@BatchSize(size = 100)
@Table(name = "taxonomy_node")
public class TaxonomyNodeEntity {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_type_id", nullable = false)
    private LookupEntity levelType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private TaxonomyNodeEntity parentNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_taxonomy_node_id", nullable = false)
    private TaxonomyNodeEntity rootTaxonomyNode;

    @Column(name = "node_key", nullable = false, length = 128)
    private String nodeKey;

    @Column(name = "external_key", length = 128)
    private String externalKey;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloned_from_id")
    private TaxonomyNodeEntity clonedFromNode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TaxonomyNodeEntity() {
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

    public LookupEntity getLevelType() {
        return levelType;
    }

    public void setLevelType(LookupEntity levelType) {
        this.levelType = levelType;
    }

    public TaxonomyNodeEntity getParentNode() {
        return parentNode;
    }

    public void setParentNode(TaxonomyNodeEntity parentNode) {
        this.parentNode = parentNode;
    }

    public TaxonomyNodeEntity getRootTaxonomyNode() {
        return rootTaxonomyNode;
    }

    public void setRootTaxonomyNode(TaxonomyNodeEntity rootTaxonomyNode) {
        this.rootTaxonomyNode = rootTaxonomyNode;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public void setExternalKey(String externalKey) {
        this.externalKey = externalKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public TaxonomyNodeEntity getClonedFromNode() {
        return clonedFromNode;
    }

    public void setClonedFromNode(TaxonomyNodeEntity clonedFromNode) {
        this.clonedFromNode = clonedFromNode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        TaxonomyNodeEntity that = (TaxonomyNodeEntity) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
