package com.clearleaf.api;

import com.clearleaf.api.entity.LookupEntity;
import com.clearleaf.api.repository.LookupRepository;
import com.clearleaf.api.repository.LookupSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class CommonLookupService {
    private final LookupRepository lookups;

    public CommonLookupService(LookupRepository lookups) {
        this.lookups = lookups;
    }

    @Transactional(readOnly = true)
    public Page<LookupResponse> list(UUID tenantId, String lookupType, String status, Pageable pageable) {
        LookupType type = parseLookupType(lookupType);
        Specification<LookupEntity> specification = LookupSpecifications.tenant(tenantId)
                .and(LookupSpecifications.byType(type))
                .and(statusSpecification(status));
        return lookups.findAll(specification, pageable).map(this::toResponse);
    }

    private LookupType parseLookupType(String lookupType) {
        if (lookupType == null || lookupType.isBlank()) {
            throw new IllegalArgumentException("lookupType is required");
        }
        try {
            return LookupType.valueOf(lookupType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported lookupType: " + lookupType);
        }
    }

    private Specification<LookupEntity> statusSpecification(String status) {
        String normalized = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
        return switch (normalized) {
            case "ACTIVE" -> LookupSpecifications.activeOnly();
            case "INACTIVE" -> (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("active"));
            case "ALL" -> Specification.where(null);
            default -> throw new IllegalArgumentException("Unsupported lookup status: " + status);
        };
    }

    private LookupResponse toResponse(LookupEntity lookup) {
        return new LookupResponse(
                lookup.getId(),
                lookup.getLookupType().name(),
                lookup.getLookupCode(),
                lookup.getLookupMeaning(),
                lookup.getLookupDescription(),
                lookup.getSortOrder(),
                lookup.isActive());
    }
}
