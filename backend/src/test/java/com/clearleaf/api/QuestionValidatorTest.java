package com.clearleaf.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestionValidatorTest {
    private final QuestionValidator validator = new QuestionValidator();
    private final AnswerScorer scorer = new AnswerScorer();

    @Test
    void acceptsValidReadyForTestMultipleSelectQuestion() {
        QuestionDraft question = multipleSelect();

        assertThat(validator.validate(question)).isEmpty();
        assertThat(scorer.exactMatch(question, Set.of("A", "C"))).isTrue();
        assertThat(scorer.exactMatch(question, Set.of("A"))).isFalse();
        assertThat(scorer.exactMatch(question, Set.of("A", "B", "C"))).isFalse();
    }

    @Test
    void rejectsMultipleSelectWithoutAnIncorrectOption() {
        QuestionDraft question = new QuestionDraft(
                QuestionType.MULTIPLE_SELECT, Difficulty.HARD, WorkflowStatus.DRAFT,
                "Select primes", null, null, null,
                List.of(new QuestionOption("A", "2", true), new QuestionOption("B", "3", true)));

        assertThat(validator.validate(question))
                .contains("Multiple-select questions require at least two correct options and one incorrect option");
    }

    @Test
    void readyForTestQuestionRequiresProvenanceAndExplanation() {
        QuestionDraft question = new QuestionDraft(
                QuestionType.SINGLE_SELECT, Difficulty.EASY, WorkflowStatus.READY_FOR_TEST,
                "What is 2 + 2?", null, null, null,
                List.of(new QuestionOption("A", "4", true), new QuestionOption("B", "5", false)));

        assertThat(validator.validate(question))
                .contains("Ready-for-test questions require an explanation")
                .contains("Ready-for-test questions require provenance")
                .contains("Ready-for-test questions require a license category");
    }

    private QuestionDraft multipleSelect() {
        return new QuestionDraft(
                QuestionType.MULTIPLE_SELECT, Difficulty.MEDIUM, WorkflowStatus.READY_FOR_TEST,
                "Select the prime numbers.", "Two and five are prime.", "NCERT Math Grade 5",
                "OFFICIAL_CURRICULUM",
                List.of(
                        new QuestionOption("A", "2", true),
                        new QuestionOption("B", "4", false),
                        new QuestionOption("C", "5", true)));
    }
}
