package com.clearleaf.api;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxonomyService {
    private final JdbcClient jdbc;

    public TaxonomyService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<TaxonomyLevelType> listLevelTypes() {
        return jdbc.sql("""
                SELECT id, level_key, display_name, allowed_parent_key, sort_order, active
                FROM taxonomy_level_type ORDER BY sort_order
                """)
                .query((rs, rowNum) -> new TaxonomyLevelType(
                        rs.getObject("id", UUID.class),
                        rs.getString("level_key"),
                        rs.getString("display_name"),
                        rs.getString("allowed_parent_key"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("active")))
                .list();
    }

    public List<TaxonomyNode> listNodes(String status) {
        String normalized = normalizeStatusFilter(status);
        String sql = """
                SELECT id, level_type_id, parent_id, node_key, display_name, status, sort_order
                FROM taxonomy_node
                """;
        if (!"ALL".equals(normalized)) {
            sql += " WHERE status = :status";
        }
        sql += " ORDER BY sort_order, display_name";
        var query = jdbc.sql(sql);
        if (!"ALL".equals(normalized)) {
            query = query.param("status", normalized);
        }
        return query.query((rs, rowNum) -> node(rs)).list();
    }

    @Transactional
    public TaxonomyLevelType createLevelType(CreateTaxonomyLevelTypeRequest request) {
        String levelKey = requireText(request.levelKey(), "levelKey").toUpperCase();
        String parentKey = request.allowedParentKey() == null
                ? null
                : requireText(request.allowedParentKey(), "allowedParentKey").toUpperCase();
        if (parentKey != null) {
            findActiveLevel(parentKey);
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO taxonomy_level_type
                    (id, level_key, display_name, allowed_parent_key, sort_order, active)
                VALUES (:id, :levelKey, :displayName, :parentKey, :sortOrder, TRUE)
                """)
                .param("id", id)
                .param("levelKey", levelKey)
                .param("displayName", requireText(request.displayName(), "displayName"))
                .param("parentKey", parentKey)
                .param("sortOrder", request.sortOrder())
                .update();
        return findActiveLevel(levelKey);
    }

    @Transactional
    public void deactivateLevelType(String levelKey) {
        long references = jdbc.sql("""
                SELECT COUNT(*) FROM taxonomy_node n
                JOIN taxonomy_level_type t ON t.id = n.level_type_id
                WHERE t.level_key = :levelKey
                """)
                .param("levelKey", levelKey)
                .query(Long.class)
                .single();
        if (references > 0) {
            throw new IllegalStateException("Level types with taxonomy values cannot be deactivated");
        }
        int changed = jdbc.sql("UPDATE taxonomy_level_type SET active = FALSE WHERE level_key = :levelKey")
                .param("levelKey", levelKey)
                .update();
        if (changed != 1) {
            throw new IllegalArgumentException("Unknown taxonomy level: " + levelKey);
        }
    }

    @Transactional
    public TaxonomyNode createNode(CreateTaxonomyNodeRequest request) {
        TaxonomyLevelType level = findActiveLevel(request.levelKey());
        validateParent(level, request.parentId());
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO taxonomy_node
                    (id, level_type_id, parent_id, node_key, display_name, status, sort_order)
                VALUES (:id, :levelTypeId, :parentId, :nodeKey, :displayName, 'ACTIVE', :sortOrder)
                """)
                .param("id", id)
                .param("levelTypeId", level.id())
                .param("parentId", request.parentId())
                .param("nodeKey", requireText(request.nodeKey(), "nodeKey"))
                .param("displayName", requireText(request.displayName(), "displayName"))
                .param("sortOrder", request.sortOrder())
                .update();
        return findNode(id);
    }

    @Transactional
    public TaxonomyNode updateNode(UUID id, UpdateTaxonomyNodeRequest request) {
        TaxonomyNode current = findNode(requireUuid(id, "id"));
        TaxonomyLevelType level = findActiveLevel(request.levelKey());
        validateParent(level, request.parentId());
        String status = normalizeStatus(request.status(), current.status());
        jdbc.sql("""
                UPDATE taxonomy_node
                SET level_type_id = :levelTypeId,
                    parent_id = :parentId,
                    node_key = :nodeKey,
                    display_name = :displayName,
                    status = :status,
                    sort_order = :sortOrder,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """)
                .param("id", id)
                .param("levelTypeId", level.id())
                .param("parentId", request.parentId())
                .param("nodeKey", requireText(request.nodeKey(), "nodeKey"))
                .param("displayName", requireText(request.displayName(), "displayName"))
                .param("status", status)
                .param("sortOrder", request.sortOrder())
                .update();
        return findNode(id);
    }

    @Transactional
    public void deactivate(UUID id) {
        int changed = jdbc.sql("UPDATE taxonomy_node SET status = 'INACTIVE', updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", id)
                .update();
        if (changed != 1) {
            throw new IllegalArgumentException("Unknown taxonomy node: " + id);
        }
    }

    @Transactional
    public void deleteUnused(UUID id) {
        if (count("SELECT COUNT(*) FROM taxonomy_node WHERE parent_id = :id", id) > 0
                || count("SELECT COUNT(*) FROM question WHERE taxonomy_node_id = :id", id) > 0) {
            throw new IllegalStateException("Referenced taxonomy nodes must be deactivated instead of deleted");
        }
        int changed = jdbc.sql("DELETE FROM taxonomy_node WHERE id = :id").param("id", id).update();
        if (changed != 1) {
            throw new IllegalArgumentException("Unknown taxonomy node: " + id);
        }
    }

    @Transactional
    public TaxonomyCloneResponse cloneEdition(CloneTaxonomyEditionRequest request) {
        UUID sourceEditionId = requireUuid(request.sourceEditionId(), "sourceEditionId");
        TaxonomyNode source = findNode(sourceEditionId);
        String sourceLevelKey = levelKey(source.levelTypeId());
        if (!"EDITION".equals(sourceLevelKey)) {
            throw new IllegalArgumentException("sourceEditionId must point to an edition node");
        }
        TaxonomyNode curriculum = findNode(requiredParentId(source));
        if (!"CURRICULUM".equals(levelKey(curriculum.levelTypeId()))) {
            throw new IllegalArgumentException("edition must be attached to a curriculum node");
        }
        String clonedKey = requireText(request.clonedEditionKey(), "clonedEditionKey");
        String clonedName = requireText(request.clonedEditionDisplayName(), "clonedEditionDisplayName");
        Map<UUID, UUID> clones = new LinkedHashMap<>();
        cloneSubtree(sourceEditionId, curriculum.id(), clonedKey, clonedName, clones, true);
        UUID cloneId = clones.get(sourceEditionId);
        TaxonomyNode clone = findNode(cloneId);
        return new TaxonomyCloneResponse(clone.id(), curriculum.id(), clone.nodeKey(), clone.displayName(), clone.status());
    }

    @Transactional
    public TaxonomyCloneResponse activateEdition(UUID editionId) {
        TaxonomyNode edition = findNode(requireUuid(editionId, "editionId"));
        if (!"EDITION".equals(levelKey(edition.levelTypeId()))) {
            throw new IllegalArgumentException("editionId must point to an edition node");
        }
        UUID curriculumId = requiredParentId(edition);
        List<UUID> siblingEditionIds = jdbc.sql("""
                SELECT id FROM taxonomy_node
                WHERE parent_id = :curriculumId
                  AND status IN ('ACTIVE', 'DRAFT', 'INACTIVE')
                  AND id <> :editionId
                """)
                .param("curriculumId", curriculumId)
                .param("editionId", editionId)
                .query(UUID.class)
                .list();
        for (UUID siblingId : siblingEditionIds) {
            setSubtreeStatus(siblingId, "INACTIVE");
        }
        setSubtreeStatus(editionId, "ACTIVE");
        jdbc.sql("""
                INSERT INTO taxonomy_edition_state (curriculum_id, active_edition_id)
                VALUES (:curriculumId, :editionId)
                ON CONFLICT (curriculum_id) DO UPDATE
                    SET active_edition_id = EXCLUDED.active_edition_id,
                        updated_at = CURRENT_TIMESTAMP
                """)
                .param("curriculumId", curriculumId)
                .param("editionId", editionId)
                .update();
        return new TaxonomyCloneResponse(edition.id(), curriculumId, edition.nodeKey(), edition.displayName(), "ACTIVE");
    }

    private long count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Long.class).single();
    }

    private void validateParent(TaxonomyLevelType level, UUID parentId) {
        if (level.allowedParentKey() == null) {
            if (parentId != null) {
                throw new IllegalArgumentException(level.levelKey() + " must not have a parent");
            }
            return;
        }
        if (parentId == null) {
            throw new IllegalArgumentException(level.levelKey() + " requires parent " + level.allowedParentKey());
        }
        String actualParentKey = jdbc.sql("""
                SELECT t.level_key
                FROM taxonomy_node n JOIN taxonomy_level_type t ON t.id = n.level_type_id
                WHERE n.id = :id AND n.status = 'ACTIVE'
                """)
                .param("id", parentId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Parent taxonomy node is missing or inactive"));
        if (!level.allowedParentKey().equals(actualParentKey)) {
            throw new IllegalArgumentException(level.levelKey() + " requires parent " + level.allowedParentKey());
        }
    }

    private TaxonomyLevelType findActiveLevel(String levelKey) {
        return jdbc.sql("""
                SELECT id, level_key, display_name, allowed_parent_key, sort_order, active
                FROM taxonomy_level_type WHERE level_key = :levelKey AND active = TRUE
                """)
                .param("levelKey", requireText(levelKey, "levelKey"))
                .query((rs, rowNum) -> new TaxonomyLevelType(
                        rs.getObject("id", UUID.class),
                        rs.getString("level_key"),
                        rs.getString("display_name"),
                        rs.getString("allowed_parent_key"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("active")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive taxonomy level: " + levelKey));
    }

    private TaxonomyNode findNode(UUID id) {
        return jdbc.sql("""
                SELECT id, level_type_id, parent_id, node_key, display_name, status, sort_order
                FROM taxonomy_node WHERE id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> node(rs))
                .single();
    }

    private String levelKey(UUID levelTypeId) {
        return jdbc.sql("SELECT level_key FROM taxonomy_level_type WHERE id = :id")
                .param("id", levelTypeId)
                .query(String.class)
                .single();
    }

    private UUID requiredParentId(TaxonomyNode node) {
        if (node.parentId() == null) {
            throw new IllegalArgumentException("taxonomy node requires a parent");
        }
        return node.parentId();
    }

    private void cloneSubtree(UUID sourceNodeId, UUID targetParentId, String rootKey, String rootDisplayName, Map<UUID, UUID> clones, boolean root) {
        TaxonomyNode source = findNode(sourceNodeId);
        UUID cloneId = UUID.randomUUID();
        clones.put(sourceNodeId, cloneId);
        jdbc.sql("""
                INSERT INTO taxonomy_node (
                    id, level_type_id, parent_id, node_key, display_name, status, sort_order, cloned_from_id
                ) VALUES (
                    :id, :levelTypeId, :parentId, :nodeKey, :displayName, 'DRAFT', :sortOrder, :clonedFromId
                )
                """)
                .param("id", cloneId)
                .param("levelTypeId", source.levelTypeId())
                .param("parentId", targetParentId)
                .param("nodeKey", root ? rootKey : source.nodeKey())
                .param("displayName", root ? rootDisplayName : source.displayName())
                .param("sortOrder", source.sortOrder())
                .param("clonedFromId", sourceNodeId)
                .update();
        List<TaxonomyNode> children = jdbc.sql("""
                SELECT id, level_type_id, parent_id, node_key, display_name, status, sort_order
                FROM taxonomy_node
                WHERE parent_id = :id
                ORDER BY sort_order, display_name
                """)
                .param("id", sourceNodeId)
                .query((rs, rowNum) -> node(rs))
                .list();
        for (TaxonomyNode child : children) {
            cloneSubtree(child.id(), cloneId, child.nodeKey(), child.displayName(), clones, false);
        }
    }

    private void setSubtreeStatus(UUID rootId, String status) {
        List<UUID> ids = subtreeIds(rootId);
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("UPDATE taxonomy_node SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id IN (:ids)")
                .param("status", status)
                .param("ids", ids)
                .update();
    }

    private List<UUID> subtreeIds(UUID rootId) {
        return jdbc.sql("""
                WITH RECURSIVE subtree AS (
                    SELECT id FROM taxonomy_node WHERE id = :id
                    UNION ALL
                    SELECT n.id
                    FROM taxonomy_node n
                    JOIN subtree s ON n.parent_id = s.id
                )
                SELECT id FROM subtree
                """)
                .param("id", rootId)
                .query(UUID.class)
                .list();
    }

    private UUID requireUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private TaxonomyNode node(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaxonomyNode(
                rs.getObject("id", UUID.class),
                rs.getObject("level_type_id", UUID.class),
                rs.getObject("parent_id", UUID.class),
                rs.getString("node_key"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getInt("sort_order"));
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.trim().toUpperCase();
        return "ALL".equals(normalized) ? "ALL" : normalizeStatus(normalized, "ACTIVE");
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback == null || fallback.isBlank() ? "ACTIVE" : fallback.toUpperCase();
        }
        String normalized = status.trim().toUpperCase();
        if (!normalized.equals("DRAFT")
                && !normalized.equals("ACTIVE")
                && !normalized.equals("INACTIVE")
                && !normalized.equals("RETIRED")
                && !normalized.equals("ARCHIVED")) {
            throw new IllegalArgumentException("Invalid taxonomy status: " + status);
        }
        return normalized;
    }
}
