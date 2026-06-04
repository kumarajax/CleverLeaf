package com.clearleaf.api.repository;

import com.clearleaf.api.entity.AssignedTestImportRowEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignedTestImportRowRepository extends JpaRepository<AssignedTestImportRowEntity, UUID> {
    List<AssignedTestImportRowEntity> findTop200ByJob_IdOrderByLineNumberAsc(UUID jobId);
}
