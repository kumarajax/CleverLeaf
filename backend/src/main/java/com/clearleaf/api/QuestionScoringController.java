package com.clearleaf.api;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionScoringController {
    private final QuestionValidator validator = new QuestionValidator();
    private final AnswerScorer scorer = new AnswerScorer();

    @PostMapping("/score")
    public Map<String, Boolean> score(@RequestBody ScoreQuestionRequest request) {
        List<String> errors = validator.validate(request.question());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Question is invalid: " + String.join("; ", errors));
        }
        return Map.of("correct", scorer.exactMatch(request.question(), request.submittedOptionKeys()));
    }
}
