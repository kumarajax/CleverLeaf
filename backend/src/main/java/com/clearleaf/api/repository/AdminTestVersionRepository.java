package com.clearleaf.api.repository;

import com.clearleaf.api.entity.AdminTestVersionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminTestVersionRepository extends JpaRepository<AdminTestVersionEntity, UUID> {
    Optional<AdminTestVersionEntity> findFirstByTest_IdOrderByVersionNumberDesc(UUID testId);
    Optional<AdminTestVersionEntity> findFirstByTest_IdAndTenantIdOrderByVersionNumberDesc(UUID testId, UUID tenantId);
    Optional<AdminTestVersionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(value = """
            select version.*
            from admin_test_version version
            join admin_test test on test.id = version.test_id
            where test.creator_subject = :creatorSubject
              and test.tenant_id = :tenantId
              and version.tenant_id = :tenantId
              and version.version_number = (
                select max(inner_version.version_number)
                from admin_test_version inner_version
                where inner_version.test_id = test.id
                  and inner_version.tenant_id = :tenantId
              )
              and (
                :query = ''
                or lower(test.public_key) like concat('%', :query, '%')
                or lower(test.name) like concat('%', :query, '%')
                or lower(test.status) like concat('%', :query, '%')
                or to_char(test.created_at, 'YYYY-MM-DD') like concat('%', :query, '%')
                or lower(to_char(test.created_at, 'Mon DD, YYYY')) like concat('%', :query, '%')
              )
            order by test.created_at desc
            limit :limit
            """, nativeQuery = true)
    List<AdminTestVersionEntity> searchLatestForCreator(
            @Param("tenantId") UUID tenantId,
            @Param("creatorSubject") String creatorSubject,
            @Param("query") String query,
            @Param("limit") int limit);
}
