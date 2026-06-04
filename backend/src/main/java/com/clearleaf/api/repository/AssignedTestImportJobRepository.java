package com.clearleaf.api.repository;

import com.clearleaf.api.entity.AssignedTestImportJobEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignedTestImportJobRepository extends JpaRepository<AssignedTestImportJobEntity, UUID> {
    Optional<AssignedTestImportJobEntity> findByIdAndActorSubject(UUID id, String actorSubject);
    List<AssignedTestImportJobEntity> findByActorSubjectOrderByCreatedAtDesc(String actorSubject);
}
