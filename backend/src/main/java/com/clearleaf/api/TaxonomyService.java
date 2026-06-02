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
    public Page<TaxonomyNode> listNodes(String status, UUID parentNodeId, boolean includeDescendants, Pageable pageable) {
        String normalized = normalizeStatusFilter(status);
        if (parentNodeId == null) {
            Specification<TaxonomyNodeEntity> specification = statusSpecification(normalized);
            return nodes.findAll(specification, pageable).map(this::toNode);
        }
        TaxonomyNodeEntity parent = findNode(parentNodeId);
        if (includeDescendants) {
            return subtree(parent.getId(), normalized, pageable);
        }
        Specification<TaxonomyNodeEntity> specification = statusSpecification(normalized)
                .and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("parentNode").get("id"), parent.getId()));
        return nodes.findAll(specification, pageable).map(this::toNode);
    }

    @Transactional
    public TaxonomyNode createNode(CreateTaxonomyNodeRequest request) {
        TaxonomyLevelDefinition definition = levelDefinition(requireText(request.levelKey(), "levelKey"));
        LookupEntity level = lookupForDefinition(definition);
        TaxonomyNodeEntity parent = validateParent(definition, request.parentId());

        TaxonomyNodeEntity node = new TaxonomyNodeEntity();
        node.setId(UUID.randomUUID());
        node.setLevelType(level);
        node.setParentNode(parent);
        node.setNodeKey(request.nodeKey());
        node.setDisplayName(requireText(request.displayName(), "displayName"));
        node.setStatus("ACTIVE");
        node.setSortOrder(request.sortOrder());
        return toNode(nodes.save(node));
    }

    @Transactional
    public TaxonomyNode updateNode(UUID id, UpdateTaxonomyNodeRequest request) {
        TaxonomyNodeEntity current = findNode(requireUuid(id, "id"));
        TaxonomyLevelDefinition definition = levelDefinition(requireText(request.levelKey(), "levelKey"));
        LookupEntity level = lookupForDefinition(definition);
        TaxonomyNodeEntity parent = validateParent(definition, request.parentId());

        current.setLevelType(level);
        current.setParentNode(parent);
        current.setNodeKey(request.nodeKey());
        current.setDisplayName(requireText(request.displayName(), "displayName"));
        current.setStatus(normalizeStatus(request.status(), current.getStatus()));
        current.setSortOrder(request.sortOrder());
        return toNode(nodes.save(current));
    }

    @Transactional
    public void deactivate(UUID id) {
        TaxonomyNodeEntity current = findNode(requireUuid(id, "id"));
        current.setStatus("INACTIVE");
        nodes.save(current);
    }

    @Transactional
    public void deleteUnused(UUID id) {
        UUID nodeId = requireUuid(id, "id");
        if (nodes.existsByParentNode_Id(nodeId) || questions.existsByTaxonomyAssignments_TaxonomyNode_Id(nodeId)) {
            throw new IllegalStateException("Referenced taxonomy nodes must be deactivated instead of deleted");
        }
        nodes.deleteById(nodeId);
    }

    @Transactional
    public TaxonomyCloneResponse cloneEdition(CloneTaxonomyEditionRequest request) {
        UUID sourceEditionId = requireUuid(request.sourceEditionId(), "sourceEditionId");
        TaxonomyNodeEntity source = findNode(sourceEditionId);
        if (!"EDITION".equals(levelCode(source))) {
            throw new IllegalArgumentException("sourceEditionId must point to an edition node");
        }
        TaxonomyNodeEntity curriculum = requiredParent(source);
        if (!"CURRICULUM".equals(levelCode(curriculum))) {
            throw new IllegalArgumentException("edition must be attached to a curriculum node");
        }
        String clonedKey = requireText(request.clonedEditionKey(), "clonedEditionKey");
        String clonedName = requireText(request.clonedEditionDisplayName(), "clonedEditionDisplayName");
        TaxonomyNodeEntity clone = cloneSubtree(source, curriculum, clonedKey, clonedName, true, new java.util.HashSet<>());
        return new TaxonomyCloneResponse(clone.getId(), curriculum.getId(), clone.getNodeKey(), clone.getDisplayName(), clone.getStatus());
    }

    @Transactional
    public TaxonomyCloneResponse activateEdition(UUID editionId) {
        TaxonomyNodeEntity edition = findNode(requireUuid(editionId, "editionId"));
        if (!"EDITION".equals(levelCode(edition))) {
            throw new IllegalArgumentException("editionId must point to an edition node");
        }
        TaxonomyNodeEntity curriculum = requiredParent(edition);
        if (!"CURRICULUM".equals(levelCode(curriculum))) {
            throw new IllegalArgumentException("edition must be attached to a curriculum node");
        }

        List<TaxonomyNodeEntity> siblingEditions = nodes.findByParentNode_IdAndIdNotOrderBySortOrderAscDisplayNameAsc(curriculum.getId(), edition.getId());
        for (TaxonomyNodeEntity sibling : siblingEditions) {
            setSubtreeStatus(sibling.getId(), "INACTIVE");
        }
        setSubtreeStatus(edition.getId(), "ACTIVE");

        TaxonomyEditionStateEntity editionState = editionStates.findByCurriculumId(curriculum.getId())
                .orElseGet(TaxonomyEditionStateEntity::new);
        editionState.setCurriculumId(curriculum.getId());
        editionState.setActiveEditionNode(edition);
        editionStates.save(editionState);

        return new TaxonomyCloneResponse(edition.getId(), curriculum.getId(), edition.getNodeKey(), edition.getDisplayName(), "ACTIVE");
    }

    private TaxonomyNodeEntity cloneSubtree(TaxonomyNodeEntity source, TaxonomyNodeEntity targetParent, String rootKey, String rootDisplayName, boolean root, Set<UUID> visited) {
        if (!visited.add(source.getId())) {
            throw new IllegalStateException("Cannot clone taxonomy with cyclic parent relationships");
        }
        TaxonomyNodeEntity clone = new TaxonomyNodeEntity();
        clone.setId(UUID.randomUUID());
        clone.setLevelType(source.getLevelType());
        clone.setParentNode(targetParent);
        clone.setNodeKey(root ? rootKey : source.getNodeKey());
        clone.setDisplayName(root ? rootDisplayName : source.getDisplayName());
        clone.setStatus("DRAFT");
        clone.setSortOrder(source.getSortOrder());
        clone.setClonedFromNode(source);
        TaxonomyNodeEntity saved = nodes.save(clone);
        for (TaxonomyNodeEntity child : nodes.findByParentNode_IdOrderBySortOrderAscDisplayNameAsc(source.getId())) {
            cloneSubtree(child, saved, child.getNodeKey(), child.getDisplayName(), false, visited);
        }
        return saved;
    }

    private void setSubtreeStatus(UUID rootId, String status) {
        Deque<UUID> queue = new ArrayDeque<>();
        Set<UUID> visited = new java.util.HashSet<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID currentId = queue.removeFirst();
            if (!visited.add(currentId)) {
                continue;
            }
            TaxonomyNodeEntity current = findNode(currentId);
            current.setStatus(status);
            nodes.save(current);
            for (TaxonomyNodeEntity child : nodes.findByParentNode_IdOrderBySortOrderAscDisplayNameAsc(currentId)) {
                queue.addLast(child.getId());
            }
        }
    }

    private TaxonomyNodeEntity validateParent(TaxonomyLevelDefinition definition, UUID parentId) {
        if (definition.allowedParentKey() == null) {
            if (parentId != null) {
                throw new IllegalArgumentException(definition.lookupCode() + " must not have a parent");
            }
            return null;
        }
        if (parentId == null) {
            throw new IllegalArgumentException(definition.lookupCode() + " requires parent " + definition.allowedParentKey());
        }
        TaxonomyNodeEntity parent = findNode(parentId);
        if (!"ACTIVE".equals(parent.getStatus())) {
            throw new IllegalArgumentException("Parent taxonomy node is missing or inactive");
        }
        if (!definition.allowedParentKey().equals(levelCode(parent))) {
            throw new IllegalArgumentException(definition.lookupCode() + " requires parent " + definition.allowedParentKey());
        }
        return parent;
    }

    private TaxonomyNodeEntity requiredParent(TaxonomyNodeEntity node) {
        if (node.getParentNode() == null) {
            throw new IllegalArgumentException("taxonomy node requires a parent");
        }
        return node.getParentNode();
    }

    private TaxonomyNodeEntity findNode(UUID id) {
        return nodes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown taxonomy node: " + id));
    }

    private LookupEntity lookupForDefinition(TaxonomyLevelDefinition definition) {
        LookupEntity lookup = lookups.findByLookupTypeAndLookupCodeIgnoreCase(LookupType.TAXONOMY_TYPE, definition.lookupCode())
                .orElseGet(() -> {
                    LookupEntity created = new LookupEntity(definition.seedId(), LookupType.TAXONOMY_TYPE, definition.lookupCode(), definition.meaning(), definition.description(), definition.sortOrder(), true);
                    created.setId(definition.seedId());
                    return created;
                });
        if (lookup.getId() == null) {
            lookup.setId(definition.seedId());
        }
        lookup.setLookupType(LookupType.TAXONOMY_TYPE);
        lookup.setLookupCode(definition.lookupCode());
        if (lookup.getLookupMeaning() == null || lookup.getLookupMeaning().isBlank()) {
            lookup.setLookupMeaning(definition.meaning());
        }
        if (lookup.getLookupDescription() == null || lookup.getLookupDescription().isBlank()) {
            lookup.setLookupDescription(definition.description());
        }
        if (lookup.getSortOrder() == 0) {
            lookup.setSortOrder(definition.sortOrder());
        }
        lookup.setActive(true);
        return lookups.save(lookup);
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

    private Page<TaxonomyNode> subtree(UUID rootId, String statusFilter, Pageable pageable) {
        Deque<UUID> queue = new ArrayDeque<>();
        List<UUID> ids = new java.util.ArrayList<>();
        Set<UUID> visited = new java.util.HashSet<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID currentId = queue.removeFirst();
            if (!visited.add(currentId)) {
                continue;
            }
            for (TaxonomyNodeEntity child : nodes.findByParentNode_IdOrderBySortOrderAscDisplayNameAsc(currentId)) {
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
                entity.getNodeKey(),
                entity.getDisplayName(),
                entity.getStatus(),
                entity.getSortOrder());
    }

    private TaxonomyLevelDefinition levelDefinition(String levelKey) {
        return TaxonomyLevelDefinition.fromCode(levelKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown taxonomy level: " + levelKey));
    }

    private String levelCode(TaxonomyNodeEntity node) {
        return node.getLevelType().getLookupCode();
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
