package com.clearleaf.api.repository;

import com.clearleaf.api.LookupType;
import com.clearleaf.api.entity.LookupEntity;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class LookupSpecifications {
    private LookupSpecifications() {
    }

    public static Specification<LookupEntity> byType(LookupType lookupType) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("lookupType"), lookupType);
    }

    public static Specification<LookupEntity> tenant(UUID tenantId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<LookupEntity> byCode(String lookupCode) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(criteriaBuilder.lower(root.get("lookupCode")), lookupCode.toLowerCase());
    }

    public static Specification<LookupEntity> activeOnly() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("active"));
    }
}
