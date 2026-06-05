package com.clearleaf.api;

import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/admin/questions")
public class QuestionAuthoringController {
    private final QuestionAuthoringService questions;

    public QuestionAuthoringController(QuestionAuthoringService questions) {
        this.questions = questions;
    }

    @GetMapping
    public Page<QuestionAdminRecord> list(
            @RequestParam(value = "questionType", required = false) String questionType,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam(value = "workflowStatus", required = false) String workflowStatus,
            @RequestParam(value = "workflowStatuses", required = false) List<String> workflowStatuses,
            @RequestParam(value = "taxonomyNodeId", required = false) UUID taxonomyNodeId,
            @RequestParam(value = "includeDescendants", defaultValue = "true") boolean includeDescendants,
            @RequestParam(value = "curriculumId", required = false) UUID curriculumId,
            @RequestParam(value = "editionId", required = false) UUID editionId,
            @RequestParam(value = "gradeId", required = false) UUID gradeId,
            @RequestParam(value = "subjectId", required = false) UUID subjectId,
            @RequestParam(value = "chapterId", required = false) UUID chapterId,
            @RequestParam(value = "topicId", required = false) UUID topicId,
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return questions.list(new QuestionSearchCriteria(questionType, difficulty, workflowStatus,
                workflowStatuses, taxonomyNodeId, includeDescendants, curriculumId, editionId, gradeId, subjectId, chapterId, topicId, search), pageable);
    }

    @GetMapping("/cursor")
    public QuestionCursorPage listCursor(
            @RequestParam(value = "questionType", required = false) String questionType,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam(value = "workflowStatus", required = false) String workflowStatus,
            @RequestParam(value = "workflowStatuses", required = false) List<String> workflowStatuses,
            @RequestParam(value = "taxonomyNodeId", required = false) UUID taxonomyNodeId,
            @RequestParam(value = "includeDescendants", defaultValue = "true") boolean includeDescendants,
            @RequestParam(value = "curriculumId", required = false) UUID curriculumId,
            @RequestParam(value = "editionId", required = false) UUID editionId,
            @RequestParam(value = "gradeId", required = false) UUID gradeId,
            @RequestParam(value = "subjectId", required = false) UUID subjectId,
            @RequestParam(value = "chapterId", required = false) UUID chapterId,
            @RequestParam(value = "topicId", required = false) UUID topicId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", defaultValue = "25") int size) {
        return questions.listCursor(new QuestionSearchCriteria(questionType, difficulty, workflowStatus,
                workflowStatuses, taxonomyNodeId, includeDescendants, curriculumId, editionId, gradeId, subjectId, chapterId, topicId, search),
                cursor, size);
    }

    @GetMapping("/{id}")
    public QuestionAdminRecord get(@PathVariable("id") UUID id) {
        return questions.get(id);
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
