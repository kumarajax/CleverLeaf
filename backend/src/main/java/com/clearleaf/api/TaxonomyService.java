package com.clearleaf.api;

import com.clearleaf.api.entity.LookupEntity;
import com.clearleaf.api.entity.TaxonomyEditionStateEntity;
import com.clearleaf.api.entity.TaxonomyNodeEntity;
import com.clearleaf.api.repository.LookupRepository;
import com.clearleaf.api.repository.LookupSpecifications;
import com.clearleaf.api.repository.QuestionRepository;
import com.clearleaf.api.repository.TaxonomyEditionStateRepository;
import com.clearleaf.api.repository.TaxonomyNodeRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxonomyService {
    private static final Set<String> TESTABLE_WORKFLOW_STATUSES = Set.of("ACTIVE", "READY_FOR_TEST", "PRACTICE");

    private final LookupRepository lookups;
    private final TaxonomyNodeRepository nodes;
    private final TaxonomyEditionStateRepository editionStates;
    private final QuestionRepository questions;

    public TaxonomyService(
            LookupRepository lookups,
            TaxonomyNodeRepository nodes,
            TaxonomyEditionStateRepository editionStates,
            QuestionRepository questions) {
        this.lookups = lookups;
        this.nodes = nodes;
        this.editionStates = editionStates;
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public List<LookupResponse> listTaxonomyLookups(String status) {
        Specification<LookupEntity> spec = LookupSpecifications.byType(LookupType.TAXONOMY_TYPE)
                .and(levelStatusSpecification(status));
        return lookups.findAll(spec, Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("lookupMeaning")))
                .stream()
                .map(this::toLookupResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TaxonomyNode> listNodes(UUID tenantId, String status, UUID parentNodeId, boolean includeDescendants, Pageable pageable) {
        String normalized = normalizeStatusFilter(status);
        if (parentNodeId == null) {
            Specification<TaxonomyNodeEntity> specification = tenantSpecification(tenantId).and(statusSpecification(normalized));
            return nodes.findAll(specification, pageable).map(this::toNode);
        }
        TaxonomyNodeEntity parent = findNode(tenantId, parentNodeId);
        if (includeDescendants) {
            return subtree(tenantId, parent.getId(), normalized, pageable);
        }
        Specification<TaxonomyNodeEntity> specification = tenantSpecification(tenantId).and(statusSpecification(normalized))
                .and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("parentNode").get("id"), parent.getId()));
        return nodes.findAll(specification, pageable).map(this::toNode);
    }

    @Transactional(readOnly = true)
    public List<StudentTaxonomyNode> searchActiveStudentTaxonomy(UUID tenantId, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return nodes.findAll(tenantSpecification(tenantId).and(statusSpecification("ACTIVE")), Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("displayName")))
                .stream()
                .map(this::toStudentNode)
                .filter(node -> node.questionCount() > 0)
                .filter(node -> normalizedQuery.isBlank() || matchesStudentQuery(node, normalizedQuery))
                .sorted(java.util.Comparator
                        .comparing((StudentTaxonomyNode node) -> node.gradeLabel() == null ? "" : node.gradeLabel())
                        .thenComparing(StudentTaxonomyNode::path))
                .toList();
    }

    @Transactional
    public TaxonomyNode createNode(UUID tenantId, CreateTaxonomyNodeRequest request) {
        String levelKey = normalizeLevelKey(requireText(request.levelKey(), "levelKey"));
        LookupEntity level = lookupOrCreateTaxonomyLevel(levelKey);
        TaxonomyNodeEntity parent = validateParent(tenantId, request.parentId());
        String nodeKey = normalizeNodeKey(request.nodeKey());

        TaxonomyNodeEntity node = new TaxonomyNodeEntity();
        node.setId(UUID.randomUUID());
        node.setTenantId(tenantId);
        node.setLevelType(level);
        node.setParentNode(parent);
        node.setRootTaxonomyNode(parent == null ? node : rootTaxonomyNode(parent));
        node.setNodeKey(nodeKey);
        validateRootNodeKeyAvailable(node.getRootTaxonomyNode(), nodeKey, node.getId());
        node.setExternalKey(defaultExternalKey(parent, nodeKey));
        node.setDisplayName(requireText(request.displayName(), "displayName"));
        node.setStatus("ACTIVE");
        node.setSortOrder(request.sortOrder());
        return toNode(nodes.save(node));
    }

    @Transactional
    public TaxonomyNode updateNode(UUID tenantId, UUID id, UpdateTaxonomyNodeRequest request) {
        TaxonomyNodeEntity current = findNode(tenantId, requireUuid(id, "id"));
        String levelKey = normalizeLevelKey(requireText(request.levelKey(), "levelKey"));
        LookupEntity level = lookupOrCreateTaxonomyLevel(levelKey);
        TaxonomyNodeEntity parent = validateParent(tenantId, request.parentId());
        String nodeKey = normalizeNodeKey(request.nodeKey());
        if (parent != null && isDescendant(tenantId, parent.getId(), current.getId())) {
            throw new IllegalArgumentException("Parent taxonomy node cannot be a descendant of the node being updated");
        }
        TaxonomyNodeEntity newRoot = parent == null ? current : rootTaxonomyNode(parent);
        List<TaxonomyNodeEntity> subtree = subtreeEntities(tenantId, current.getId());
        validateSubtreeRootNodeKeys(subtree, current.getId(), newRoot, nodeKey);

        current.setLevelType(level);
        current.setParentNode(parent);
        current.setRootTaxonomyNode(newRoot);
        current.setNodeKey(nodeKey);
        if (current.getExternalKey() == null || current.getExternalKey().isBlank()) {
            current.setExternalKey(defaultExternalKey(parent, nodeKey));
        }
        current.setDisplayName(requireText(request.displayName(), "displayName"));
        current.setStatus(normalizeStatus(request.status(), current.getStatus()));
        current.setSortOrder(request.sortOrder());
        TaxonomyNodeEntity saved = nodes.save(current);
        reassignSubtreeRoots(tenantId, current.getId(), newRoot);
        return toNode(saved);
    }

    @Transactional
    public void deactivate(UUID tenantId, UUID id) {
        TaxonomyNodeEntity current = findNode(tenantId, requireUuid(id, "id"));
        current.setStatus("INACTIVE");
        nodes.save(current);
    }

    @Transactional
    public void deleteUnused(UUID tenantId, UUID id) {
        UUID nodeId = requireUuid(id, "id");
        TaxonomyNodeEntity current = findNode(tenantId, nodeId);
        boolean hasChildNodes = nodes.existsByParentNode_IdAndTenantId(nodeId, tenantId);
        boolean hasQuestions = questions.existsByTaxonomyAssignments_TaxonomyNode_Id(nodeId);
        if (hasChildNodes && hasQuestions) {
            throw new IllegalStateException("This taxonomy node cannot be deleted because it has child nodes and is referenced by questions. Deactivate it instead.");
        }
        if (hasChildNodes) {
            throw new IllegalStateException("This taxonomy node cannot be deleted because it has child nodes. Deactivate it instead.");
        }
        if (hasQuestions) {
            throw new IllegalStateException("This taxonomy node cannot be deleted because it is referenced by questions. Deactivate it instead.");
        }
        nodes.delete(current);
    }

    @Transactional
    public TaxonomyCloneResponse cloneEdition(UUID tenantId, CloneTaxonomyEditionRequest request) {
        UUID sourceEditionId = requireUuid(request.sourceEditionId(), "sourceEditionId");
        TaxonomyNodeEntity source = findNode(tenantId, sourceEditionId);
        if (!"EDITION".equals(levelCode(source))) {
            throw new IllegalArgumentException("sourceEditionId must point to an edition node");
        }
        TaxonomyNodeEntity curriculum = requiredParent(source);
        if (!"CURRICULUM".equals(levelCode(curriculum))) {
            throw new IllegalArgumentException("edition must be attached to a curriculum node");
        }
        String clonedKey = requireText(request.clonedEditionKey(), "clonedEditionKey");
        String clonedName = requireText(request.clonedEditionDisplayName(), "clonedEditionDisplayName");
        TaxonomyNodeEntity clone = cloneSubtree(tenantId, source, curriculum, clonedKey, clonedName, true, new java.util.HashSet<>());
        return new TaxonomyCloneResponse(clone.getId(), curriculum.getId(), clone.getNodeKey(), clone.getDisplayName(), clone.getStatus());
    }

    @Transactional
    public TaxonomyCloneResponse activateEdition(UUID tenantId, UUID editionId) {
        TaxonomyNodeEntity edition = findNode(tenantId, requireUuid(editionId, "editionId"));
        if (!"EDITION".equals(levelCode(edition))) {
            throw new IllegalArgumentException("editionId must point to an edition node");
        }
        TaxonomyNodeEntity curriculum = requiredParent(edition);
        if (!"CURRICULUM".equals(levelCode(curriculum))) {
            throw new IllegalArgumentException("edition must be attached to a curriculum node");
        }

        List<TaxonomyNodeEntity> siblingEditions = nodes.findByParentNode_IdAndTenantIdAndIdNotOrderBySortOrderAscDisplayNameAsc(curriculum.getId(), tenantId, edition.getId());
        for (TaxonomyNodeEntity sibling : siblingEditions) {
            setSubtreeStatus(tenantId, sibling.getId(), "INACTIVE");
        }
        setSubtreeStatus(tenantId, edition.getId(), "ACTIVE");

        TaxonomyEditionStateEntity editionState = editionStates.findByCurriculumId(curriculum.getId())
                .orElseGet(TaxonomyEditionStateEntity::new);
        editionState.setCurriculumId(curriculum.getId());
        editionState.setActiveEditionNode(edition);
        editionStates.save(editionState);

        return new TaxonomyCloneResponse(edition.getId(), curriculum.getId(), edition.getNodeKey(), edition.getDisplayName(), "ACTIVE");
    }

    private TaxonomyNodeEntity cloneSubtree(UUID tenantId, TaxonomyNodeEntity source, TaxonomyNodeEntity targetParent, String rootKey, String rootDisplayName, boolean root, Set<UUID> visited) {
        if (!visited.add(source.getId())) {
            throw new IllegalStateException("Cannot clone taxonomy with cyclic parent relationships");
        }
        TaxonomyNodeEntity clone = new TaxonomyNodeEntity();
        clone.setId(UUID.randomUUID());
        clone.setTenantId(tenantId);
        clone.setLevelType(source.getLevelType());
        clone.setParentNode(targetParent);
        TaxonomyNodeEntity rootNode = targetParent == null ? clone : rootTaxonomyNode(targetParent);
        String nodeKey = normalizeNodeKey(root ? rootKey : source.getNodeKey());
        clone.setRootTaxonomyNode(rootNode);
        clone.setNodeKey(nodeKey);
        clone.setDisplayName(root ? rootDisplayName : source.getDisplayName());
        clone.setStatus("DRAFT");
        clone.setSortOrder(source.getSortOrder());
        clone.setClonedFromNode(source);
        validateRootNodeKeyAvailable(rootNode, nodeKey, clone.getId());
        TaxonomyNodeEntity saved = nodes.save(clone);
        for (TaxonomyNodeEntity child : nodes.findByParentNode_IdAndTenantIdOrderBySortOrderAscDisplayNameAsc(source.getId(), tenantId)) {
            cloneSubtree(tenantId, child, saved, child.getNodeKey(), child.getDisplayName(), false, visited);
        }
        return saved;
    }

    private void setSubtreeStatus(UUID tenantId, UUID rootId, String status) {
        Deque<UUID> queue = new ArrayDeque<>();
        Set<UUID> visited = new java.util.HashSet<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID currentId = queue.removeFirst();
            if (!visited.add(currentId)) {
                continue;
            }
            TaxonomyNodeEntity current = findNode(tenantId, currentId);
            current.setStatus(status);
            nodes.save(current);
            for (TaxonomyNodeEntity child : nodes.findByParentNode_IdAndTenantIdOrderBySortOrderAscDisplayNameAsc(currentId, tenantId)) {
                queue.addLast(child.getId());
            }
        }
    }

    private TaxonomyNodeEntity validateParent(UUID tenantId, UUID parentId) {
        if (parentId == null) return null;
        TaxonomyNodeEntity parent = findNode(tenantId, parentId);
        if (!"ACTIVE".equals(parent.getStatus())) {
            throw new IllegalArgumentException("Parent taxonomy node is missing or inactive");
        }
        return parent;
    }

    private TaxonomyNodeEntity requiredParent(TaxonomyNodeEntity node) {
        if (node.getParentNode() == null) {
            throw new IllegalArgumentException("taxonomy node requires a parent");
        }
        return node.getParentNode();
    }

    private TaxonomyNodeEntity findNode(UUID tenantId, UUID id) {
        return nodes.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown taxonomy node: " + id));
    }

    private TaxonomyNodeEntity rootTaxonomyNode(TaxonomyNodeEntity node) {
        if (node.getRootTaxonomyNode() != null) {
            return node.getRootTaxonomyNode();
        }
        TaxonomyNodeEntity current = node;
        Set<UUID> visited = new java.util.HashSet<>();
        while (current.getParentNode() != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException("Taxonomy contains a cycle");
            }
            current = current.getParentNode();
        }
        return current;
    }

    private boolean isDescendant(UUID tenantId, UUID nodeId, UUID ancestorId) {
        TaxonomyNodeEntity current = findNode(tenantId, nodeId);
        Set<UUID> visited = new java.util.HashSet<>();
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException("Taxonomy contains a cycle");
            }
            if (ancestorId.equals(current.getId())) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }

    private List<TaxonomyNodeEntity> subtreeEntities(UUID tenantId, UUID rootId) {
        Deque<UUID> queue = new ArrayDeque<>();
        List<TaxonomyNodeEntity> result = new java.util.ArrayList<>();
        Set<UUID> visited = new java.util.HashSet<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID currentId = queue.removeFirst();
            if (!visited.add(currentId)) {
                continue;
            }
            TaxonomyNodeEntity current = findNode(tenantId, currentId);
            result.add(current);
            for (TaxonomyNodeEntity child : nodes.findByParentNode_IdAndTenantIdOrderBySortOrderAscDisplayNameAsc(currentId, tenantId)) {
                queue.addLast(child.getId());
            }
        }
        return result;
    }

    private void validateRootNodeKeyAvailable(TaxonomyNodeEntity root, String nodeKey, UUID currentId) {
        if (nodes.existsByRootTaxonomyNode_IdAndTenantIdAndNodeKeyAndIdNot(root.getId(), root.getTenantId(), nodeKey, currentId)) {
            throw new IllegalArgumentException("nodeKey already exists under root taxonomy: " + root.getNodeKey());
        }
    }

    private void validateSubtreeRootNodeKeys(
            List<TaxonomyNodeEntity> subtree,
            UUID updatedNodeId,
            TaxonomyNodeEntity newRoot,
            String updatedNodeKey) {
        Set<UUID> subtreeIds = subtree.stream()
                .map(TaxonomyNodeEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> seenKeys = new java.util.HashSet<>();
        for (TaxonomyNodeEntity node : subtree) {
            String nodeKey = node.getId().equals(updatedNodeId) ? updatedNodeKey : normalizeNodeKey(node.getNodeKey());
            if (!seenKeys.add(nodeKey)) {
                throw new IllegalArgumentException("Duplicate nodeKey inside taxonomy subtree: " + nodeKey);
            }
            if (nodes.existsByRootTaxonomyNode_IdAndTenantIdAndNodeKeyAndIdNotIn(newRoot.getId(), newRoot.getTenantId(), nodeKey, subtreeIds)) {
                throw new IllegalArgumentException("nodeKey already exists under root taxonomy: " + newRoot.getNodeKey());
            }
        }
    }

    private void reassignSubtreeRoots(UUID tenantId, UUID rootId, TaxonomyNodeEntity newRoot) {
        for (TaxonomyNodeEntity node : subtreeEntities(tenantId, rootId)) {
            if (!newRoot.getId().equals(node.getRootTaxonomyNode() == null ? null : node.getRootTaxonomyNode().getId())) {
                node.setRootTaxonomyNode(newRoot);
                nodes.save(node);
            }
        }
    }

    private LookupEntity lookupOrCreateTaxonomyLevel(String levelKey) {
        return lookups.findByLookupTypeAndLookupCodeIgnoreCase(LookupType.TAXONOMY_TYPE, levelKey)
                .orElseGet(() -> lookups.save(new LookupEntity(
                        UUID.randomUUID(),
                        LookupType.TAXONOMY_TYPE,
                        levelKey,
                        displayName(levelKey),
                        "Customer taxonomy level",
                        1000,
                        true)));
    }

    private LookupResponse toLookupResponse(LookupEntity lookup) {
        return new LookupResponse(
                lookup.getId(),
                lookup.getLookupType().name(),
                lookup.getLookupCode(),
                lookup.getLookupMeaning(),
                lookup.getLookupDescription(),
                lookup.getSortOrder(),
                lookup.isActive());
    }

    private Page<TaxonomyNode> subtree(UUID tenantId, UUID rootId, String statusFilter, Pageable pageable) {
        Deque<UUID> queue = new ArrayDeque<>();
        List<UUID> ids = new java.util.ArrayList<>();
        Set<UUID> visited = new java.util.HashSet<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID currentId = queue.removeFirst();
            if (!visited.add(currentId)) {
                continue;
            }
            for (TaxonomyNodeEntity child : nodes.findByParentNode_IdAndTenantIdOrderBySortOrderAscDisplayNameAsc(currentId, tenantId)) {
                if (!visited.contains(child.getId()) && statusMatches(child.getStatus(), statusFilter)) {
                    ids.add(child.getId());
                }
                queue.addLast(child.getId());
            }
        }
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<TaxonomyNode> filtered = nodes.findByIdInOrderBySortOrderAscDisplayNameAsc(ids)
                .stream()
                .filter(node -> statusMatches(node.getStatus(), statusFilter))
                .map(this::toNode)
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    private TaxonomyNode toNode(TaxonomyNodeEntity entity) {
        return new TaxonomyNode(
                entity.getId(),
                entity.getLevelType().getId(),
                entity.getParentNode() == null ? null : entity.getParentNode().getId(),
                entity.getExternalKey(),
                entity.getNodeKey(),
                entity.getDisplayName(),
                entity.getStatus(),
                entity.getSortOrder());
    }

    private StudentTaxonomyNode toStudentNode(TaxonomyNodeEntity entity) {
        return new StudentTaxonomyNode(
                entity.getId(),
                entity.getParentNode() == null ? null : entity.getParentNode().getId(),
                entity.getExternalKey(),
                entity.getNodeKey(),
                entity.getDisplayName(),
                levelCode(entity),
                gradeLabel(entity),
                taxonomyPath(entity),
                questions.countTestableByTaxonomyNodeIds(descendantIds(entity.getTenantId(), entity.getId()), TESTABLE_WORKFLOW_STATUSES));
    }

    private List<UUID> descendantIds(UUID tenantId, UUID rootId) {
        Deque<UUID> queue = new ArrayDeque<>();
        List<UUID> result = new java.util.ArrayList<>();
        Set<UUID> visited = new java.util.HashSet<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID currentId = queue.removeFirst();
            if (!visited.add(currentId)) {
                continue;
            }
            result.add(currentId);
            for (TaxonomyNodeEntity child : nodes.findByParentNode_IdAndTenantIdOrderBySortOrderAscDisplayNameAsc(currentId, tenantId)) {
                queue.addLast(child.getId());
            }
        }
        return result;
    }

    private String levelCode(TaxonomyNodeEntity node) {
        return node.getLevelType().getLookupCode();
    }

    private boolean matchesStudentQuery(StudentTaxonomyNode node, String normalizedQuery) {
        return containsIgnoreCase(node.nodeKey(), normalizedQuery)
                || containsIgnoreCase(node.displayName(), normalizedQuery)
                || containsIgnoreCase(node.externalKey(), normalizedQuery)
                || containsIgnoreCase(node.levelKey(), normalizedQuery)
                || containsIgnoreCase(node.path(), normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private String gradeLabel(TaxonomyNodeEntity entity) {
        TaxonomyNodeEntity current = entity;
        Set<UUID> visited = new java.util.HashSet<>();
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException("Taxonomy contains a cycle");
            }
            if ("GRADE".equals(levelCode(current))) {
                return current.getDisplayName();
            }
            current = current.getParentNode();
        }
        return "Ungraded";
    }

    private String taxonomyPath(TaxonomyNodeEntity entity) {
        Deque<String> parts = new ArrayDeque<>();
        TaxonomyNodeEntity current = entity;
        Set<UUID> visited = new java.util.HashSet<>();
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException("Taxonomy contains a cycle");
            }
            parts.addFirst(current.getDisplayName());
            current = current.getParentNode();
        }
        return String.join(" / ", parts);
    }

    private String normalizeLevelKey(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("levelKey must include at least one letter or number");
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("levelKey must be 64 characters or fewer");
        }
        return normalized;
    }

    private String normalizeNodeKey(String value) {
        String normalized = requireText(value, "nodeKey").trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]+(?:_[A-Z0-9]+)*")) {
            throw new IllegalArgumentException("nodeKey must contain only uppercase letters, numbers, and single underscores between words");
        }
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("nodeKey must be 128 characters or fewer");
        }
        return normalized;
    }

    private String displayName(String value) {
        String normalized = normalizeLevelKey(value).toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder display = new StringBuilder();
        for (String word : normalized.split(" ")) {
            if (word.isBlank()) continue;
            if (!display.isEmpty()) display.append(' ');
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return display.toString();
    }

    private String defaultExternalKey(TaxonomyNodeEntity parent, String nodeKey) {
        String normalizedNodeKey = requireText(nodeKey, "nodeKey").trim().toUpperCase(Locale.ROOT);
        if (parent == null) return normalizedNodeKey;
        String parentKey = parent.getExternalKey();
        if (parentKey == null || parentKey.isBlank()) {
            parentKey = parent.getNodeKey();
        }
        return parentKey + "_" + normalizedNodeKey;
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.trim().toUpperCase();
        return "ALL".equals(normalized) ? "ALL" : normalizeStatus(normalized, "ACTIVE");
    }

    private boolean statusMatches(String value, String filter) {
        return "ALL".equals(filter) || value.equalsIgnoreCase(filter);
    }

    private Specification<TaxonomyNodeEntity> statusSpecification(String normalizedStatus) {
        if ("ALL".equals(normalizedStatus)) {
            return Specification.where(null);
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), normalizedStatus);
    }

    private Specification<TaxonomyNodeEntity> tenantSpecification(UUID tenantId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("tenantId"), tenantId);
    }

    private Specification<LookupEntity> levelStatusSpecification(String status) {
        String normalized = normalizeStatusFilter(status);
        return switch (normalized) {
            case "ALL" -> Specification.where(null);
            case "ACTIVE" -> (root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("active"));
            case "INACTIVE" -> (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("active"));
            default -> (root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("active"));
        };
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback == null || fallback.isBlank() ? "ACTIVE" : fallback.toUpperCase();
        }
        String normalized = status.trim().toUpperCase();
        if (!Set.of("DRAFT", "ACTIVE", "INACTIVE", "RETIRED", "ARCHIVED").contains(normalized)) {
            throw new IllegalArgumentException("Invalid taxonomy status: " + status);
        }
        return normalized;
    }

    private UUID requireUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

}
