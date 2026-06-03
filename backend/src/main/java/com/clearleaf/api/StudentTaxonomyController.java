package com.clearleaf.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/taxonomy")
public class StudentTaxonomyController {
    private final TaxonomyService taxonomy;

    public StudentTaxonomyController(TaxonomyService taxonomy) {
        this.taxonomy = taxonomy;
    }

    @GetMapping("/search")
    public List<StudentTaxonomyNode> search(@RequestParam(value = "query", required = false) String query) {
        return taxonomy.searchActiveStudentTaxonomy(query);
    }
}
