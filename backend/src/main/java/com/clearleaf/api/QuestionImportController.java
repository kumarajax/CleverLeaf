package com.clearleaf.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/admin/imports/questions")
public class QuestionImportController {
    private final QuestionImportService imports;

    public QuestionImportController(QuestionImportService imports) {
        this.imports = imports;
    }

    @GetMapping("/preview")
    public CsvPreviewResponse preview(@RequestParam("objectKey") String objectKey) {
        return imports.preview(objectKey);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CsvImportSummary importDrafts(@RequestParam("objectKey") String objectKey) {
        return imports.importDrafts(objectKey);
    }
}
