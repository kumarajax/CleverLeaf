package com.clearleaf.api.repository;

import com.clearleaf.api.LookupType;
import com.clearleaf.api.entity.LookupEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LookupRepository extends JpaRepository<LookupEntity, UUID>, JpaSpecificationExecutor<LookupEntity> {
    Optional<LookupEntity> findByLookupTypeAndLookupCodeIgnoreCase(LookupType lookupType, String lookupCode);
}
