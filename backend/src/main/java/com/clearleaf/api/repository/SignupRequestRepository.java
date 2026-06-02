package com.clearleaf.api.repository;

import com.clearleaf.api.entity.SignupRequestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface SignupRequestRepository extends JpaRepository<SignupRequestEntity, UUID> {

    boolean existsByEmailIgnoreCaseAndStatus(String email, String status);

    Optional<SignupRequestEntity> findFirstByApproveTokenHashOrRejectTokenHash(
            String approveTokenHash,
            String rejectTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SignupRequestEntity> findFirstByApproveTokenHashAndStatus(String approveTokenHash, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SignupRequestEntity> findFirstByRejectTokenHashAndStatus(String rejectTokenHash, String status);
}
