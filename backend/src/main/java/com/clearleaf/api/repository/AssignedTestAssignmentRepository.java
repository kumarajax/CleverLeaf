package com.clearleaf.api.repository;

import com.clearleaf.api.entity.AssignedTestAssignmentEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignedTestAssignmentRepository extends JpaRepository<AssignedTestAssignmentEntity, UUID> {
    Optional<AssignedTestAssignmentEntity> findByIdAndStudentSubject(UUID id, String studentSubject);
    Optional<AssignedTestAssignmentEntity> findByIdAndStudentSubjectIn(UUID id, Collection<String> studentSubjects);
    Optional<AssignedTestAssignmentEntity> findByIdAndStudentSubjectInAndTenantId(UUID id, Collection<String> studentSubjects, UUID tenantId);
    Optional<AssignedTestAssignmentEntity> findFirstByVersion_IdAndStudentSubjectIgnoreCaseAndStatusInOrderByAssignedAtDesc(UUID versionId, String studentSubject, Collection<String> statuses);
    Optional<AssignedTestAssignmentEntity> findFirstByVersion_IdAndStudentSubjectIgnoreCaseAndStatusInAndTenantIdOrderByAssignedAtDesc(UUID versionId, String studentSubject, Collection<String> statuses, UUID tenantId);
    List<AssignedTestAssignmentEntity> findByStudentSubjectOrderByAssignedAtDesc(String studentSubject);
    List<AssignedTestAssignmentEntity> findByStudentSubjectInOrderByAssignedAtDesc(Collection<String> studentSubjects);
    List<AssignedTestAssignmentEntity> findByStudentSubjectInAndTenantIdOrderByAssignedAtDesc(Collection<String> studentSubjects, UUID tenantId);
    List<AssignedTestAssignmentEntity> findByVersion_Test_CreatorSubjectOrderByAssignedAtDesc(String creatorSubject);
    List<AssignedTestAssignmentEntity> findByVersion_Test_CreatorSubjectAndTenantIdOrderByAssignedAtDesc(String creatorSubject, UUID tenantId);
    long countByVersion_Id(UUID versionId);
    long countByVersion_IdAndStatusNot(UUID versionId, String status);
    long countByVersion_IdAndTenantIdAndStatusNot(UUID versionId, UUID tenantId, String status);
    long countByVersion_IdAndStatus(UUID versionId, String status);
    long countByVersion_IdAndTenantIdAndStatus(UUID versionId, UUID tenantId, String status);

    @Query("""
            select count(assignment)
            from AssignedTestAssignmentEntity assignment
            where assignment.version.id = :versionId
              and assignment.status <> :excludedStatus
              and assignment.tenantId = :tenantId
              and assignment.resultsPublishedAt is not null
            """)
    long countPublishedByVersionId(@Param("versionId") UUID versionId, @Param("tenantId") UUID tenantId, @Param("excludedStatus") String excludedStatus);

    @Query("""
            select max(assignment.resultsPublishedAt)
            from AssignedTestAssignmentEntity assignment
            where assignment.version.id = :versionId
              and assignment.status <> :excludedStatus
              and assignment.tenantId = :tenantId
            """)
    Instant latestResultsPublishedAtByVersionId(@Param("versionId") UUID versionId, @Param("tenantId") UUID tenantId, @Param("excludedStatus") String excludedStatus);
}
