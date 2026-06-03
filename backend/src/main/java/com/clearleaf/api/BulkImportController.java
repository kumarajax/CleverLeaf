package com.clearleaf.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/imports/bulk")
public class BulkImportController {
    private final BulkImportService imports;

    public BulkImportController(BulkImportService imports) {
        this.imports = imports;
    }

    @GetMapping("/metadata")
    public List<BulkImportStepMetadata> metadata() {
        return imports.metadata();
    }

    @GetMapping("/{step}/preview")
    public BulkImportPreviewResponse preview(
            @PathVariable("step") BulkImportStep step,
            @RequestParam("objectKey") String objectKey) {
        return imports.preview(step, objectKey);
    }

    @PostMapping("/{step}")
    @ResponseStatus(HttpStatus.CREATED)
    public BulkImportSummary importStep(
            @PathVariable("step") BulkImportStep step,
            @RequestParam("objectKey") String objectKey) {
        return imports.importStep(step, objectKey);
    }
}
