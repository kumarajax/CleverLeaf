package com.clearleaf.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/admin/taxonomy")
public class TaxonomyController {
    private final TaxonomyService taxonomy;

    public TaxonomyController(TaxonomyService taxonomy) {
        this.taxonomy = taxonomy;
    }

    @GetMapping("/level-types")
    public List<TaxonomyLevelType> levelTypes() {
        return taxonomy.listLevelTypes();
    }

    @PostMapping("/level-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyLevelType createLevelType(@RequestBody CreateTaxonomyLevelTypeRequest request) {
        return taxonomy.createLevelType(request);
    }

    @PostMapping("/level-types/{levelKey}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateLevelType(@PathVariable("levelKey") String levelKey) {
        taxonomy.deactivateLevelType(levelKey);
    }

    @GetMapping("/nodes")
    public List<TaxonomyNode> nodes(@RequestParam(value = "status", required = false) String status) {
        return taxonomy.listNodes(status);
    }

    @PostMapping("/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyNode create(@RequestBody CreateTaxonomyNodeRequest request) {
        return taxonomy.createNode(request);
    }

    @PutMapping("/nodes/{id}")
    public TaxonomyNode update(@PathVariable("id") UUID id, @RequestBody UpdateTaxonomyNodeRequest request) {
        return taxonomy.updateNode(id, request);
    }

    @PostMapping("/editions/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyCloneResponse cloneEdition(@RequestBody CloneTaxonomyEditionRequest request) {
        return taxonomy.cloneEdition(request);
    }

    @PostMapping("/editions/{id}/activate")
    public TaxonomyCloneResponse activateEdition(@PathVariable("id") UUID id) {
        return taxonomy.activateEdition(id);
    }

    @PostMapping("/nodes/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable("id") UUID id) {
        taxonomy.deactivate(id);
    }

    @DeleteMapping("/nodes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUnused(@PathVariable("id") UUID id) {
        taxonomy.deleteUnused(id);
    }
}
