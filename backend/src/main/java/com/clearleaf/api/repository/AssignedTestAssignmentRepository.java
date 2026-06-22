package com.clearleaf.api.repository;

import com.clearleaf.api.entity.AssignedTestAssignmentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignedTestAssignmentRepository extends JpaRepository<AssignedTestAssignmentEntity, UUID> {
    Optional<AssignedTestAssignmentEntity> findByIdAndStudentSubject(UUID id, String studentSubject);
    Optional<AssignedTestAssignmentEntity> findByIdAndStudentSubjectIn(UUID id, Collection<String> studentSubjects);
    Optional<AssignedTestAssignmentEntity> findFirstByVersion_IdAndStudentSubjectIgnoreCaseAndStatusInOrderByAssignedAtDesc(UUID versionId, String studentSubject, Collection<String> statuses);
    List<AssignedTestAssignmentEntity> findByStudentSubjectOrderByAssignedAtDesc(String studentSubject);
    List<AssignedTestAssignmentEntity> findByStudentSubjectInOrderByAssignedAtDesc(Collection<String> studentSubjects);
    List<AssignedTestAssignmentEntity> findByVersion_Test_CreatorSubjectOrderByAssignedAtDesc(String creatorSubject);
    long countByVersion_Id(UUID versionId);
    long countByVersion_IdAndStatusNot(UUID versionId, String status);
    long countByVersion_IdAndStatus(UUID versionId, String status);
}
