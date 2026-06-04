package com.clearleaf.api.repository;

import com.clearleaf.api.entity.TestAttemptEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAttemptRepository extends JpaRepository<TestAttemptEntity, UUID> {
    Optional<TestAttemptEntity> findByIdAndStudentSubject(UUID id, String studentSubject);
}
