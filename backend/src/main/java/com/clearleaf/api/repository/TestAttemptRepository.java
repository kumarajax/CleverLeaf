package com.clearleaf.api.repository;

import com.clearleaf.api.entity.TestAttemptEntity;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAttemptRepository extends JpaRepository<TestAttemptEntity, UUID>, JpaSpecificationExecutor<TestAttemptEntity> {
    Optional<TestAttemptEntity> findByIdAndStudentSubject(UUID id, String studentSubject);
    Optional<TestAttemptEntity> findByIdAndStudentSubjectAndTenantId(UUID id, String studentSubject, UUID tenantId);

    Optional<TestAttemptEntity> findByAssignment_Id(UUID assignmentId);
    Optional<TestAttemptEntity> findByAssignment_IdAndTenantId(UUID assignmentId, UUID tenantId);

    Optional<TestAttemptEntity> findByAssignment_IdAndStudentSubject(UUID assignmentId, String studentSubject);

    Optional<TestAttemptEntity> findByAssignment_IdAndStudentSubjectIn(UUID assignmentId, Collection<String> studentSubjects);
    Optional<TestAttemptEntity> findByAssignment_IdAndStudentSubjectInAndTenantId(UUID assignmentId, Collection<String> studentSubjects, UUID tenantId);
}
