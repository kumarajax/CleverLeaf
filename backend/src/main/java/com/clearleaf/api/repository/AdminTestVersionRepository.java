package com.clearleaf.api.repository;

import com.clearleaf.api.entity.AdminTestVersionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminTestVersionRepository extends JpaRepository<AdminTestVersionEntity, UUID> {
    Optional<AdminTestVersionEntity> findFirstByTest_IdOrderByVersionNumberDesc(UUID testId);
}
