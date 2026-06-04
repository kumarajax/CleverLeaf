package com.clearleaf.api.repository;

import com.clearleaf.api.entity.TestAttemptQuestionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAttemptQuestionRepository extends JpaRepository<TestAttemptQuestionEntity, UUID> {
    Optional<TestAttemptQuestionEntity> findByIdAndAttempt_Id(UUID id, UUID attemptId);
}
