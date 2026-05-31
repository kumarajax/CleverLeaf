package com.clearleaf.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/questions")
public class QuestionAuthoringController {
    private final QuestionAuthoringService questions;

    public QuestionAuthoringController(QuestionAuthoringService questions) {
        this.questions = questions;
    }

    @GetMapping
    public List<QuestionAdminRecord> list() {
        return questions.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedQuestionResponse create(@RequestBody CreateQuestionRequest request) {
        return questions.create(request);
    }

    @PutMapping("/{id}")
    public QuestionAdminRecord update(@PathVariable("id") UUID id, @RequestBody CreateQuestionRequest request) {
        return questions.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        questions.delete(id);
    }
}
