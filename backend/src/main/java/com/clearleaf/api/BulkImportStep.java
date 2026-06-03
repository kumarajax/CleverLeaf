package com.clearleaf.api;

import java.util.List;

public enum BulkImportStep {
    TAXONOMIES(1, "Import Taxonomies", List.of(
            new BulkImportColumn("PublicKey", true, "Stable public taxonomy key used by later imports"),
            new BulkImportColumn("levelKey", true, "Customer taxonomy level code, such as SCHOOL, SUBJECT, or TOPIC"),
            new BulkImportColumn("ParentPublicKey", false, "Parent taxonomy PublicKey"),
            new BulkImportColumn("nodeKey", true, "Taxonomy node code"),
            new BulkImportColumn("displayName", true, "Taxonomy display name"),
            new BulkImportColumn("status", false, "ACTIVE, DRAFT, or INACTIVE"),
            new BulkImportColumn("sortOrder", false, "Sibling order"))),
    QUESTIONS(2, "Import Questions", List.of(
            new BulkImportColumn("PublicKey", true, "Stable public question key used by later imports"),
            new BulkImportColumn("RootTaxonomy", true, "Root taxonomy code, such as LANDELS"),
            new BulkImportColumn("ChildTaxonomy", true, "Child taxonomy code under the root, such as FRACTIONS or GRAMMAR"),
            new BulkImportColumn("questionType", true, "SINGLE_SELECT, MULTIPLE_SELECT, TRUE_FALSE, FILL_BLANK, or NUMERICAL"),
            new BulkImportColumn("difficulty", true, "EASY, MEDIUM, or HARD"),
            new BulkImportColumn("workflowStatus", false, "Defaults to DRAFT"),
            new BulkImportColumn("questionText", true, "Question body"),
            new BulkImportColumn("explanation", false, "Answer explanation"),
            new BulkImportColumn("sourceReference", false, "Provenance"),
            new BulkImportColumn("licenseCategory", false, "License category"),
            new BulkImportColumn("tags", false, "Comma separated tag codes"))),
    QUESTION_OPTIONS(3, "Import Question Options", List.of(
            new BulkImportColumn("QuestionPublicKey", true, "Question PublicKey"),
            new BulkImportColumn("optionKey", true, "Option key, such as A or TRUE"),
            new BulkImportColumn("optionText", true, "Visible option text"),
            new BulkImportColumn("sortOrder", false, "Option order"))),
    CORRECT_ANSWERS(4, "Import Correct Answers", List.of(
            new BulkImportColumn("QuestionPublicKey", true, "Question PublicKey"),
            new BulkImportColumn("optionKey", false, "Correct option key for option-based questions"),
            new BulkImportColumn("answerValue", false, "Accepted answer for fill-blank or numerical questions"),
            new BulkImportColumn("answerType", false, "TEXT or NUMERIC"),
            new BulkImportColumn("toleranceValue", false, "Numeric tolerance"),
            new BulkImportColumn("caseSensitive", false, "true or false"),
            new BulkImportColumn("sortOrder", false, "Answer order")));

    private final int sequence;
    private final String label;
    private final List<BulkImportColumn> columns;

    BulkImportStep(int sequence, String label, List<BulkImportColumn> columns) {
        this.sequence = sequence;
        this.label = label;
        this.columns = columns;
    }

    public int sequence() {
        return sequence;
    }

    public String label() {
        return label;
    }

    public List<BulkImportColumn> columns() {
        return columns;
    }
}
