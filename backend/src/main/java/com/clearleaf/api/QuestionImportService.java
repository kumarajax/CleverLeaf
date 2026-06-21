package com.clearleaf.api;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionImportService {
    private final MinioStorageService storage;
    private final QuestionAuthoringService authoring;
    private final QuestionValidator validator = new QuestionValidator();

    public QuestionImportService(MinioStorageService storage, QuestionAuthoringService authoring) {
        this.storage = storage;
        this.authoring = authoring;
    }

    public CsvPreviewResponse preview(String objectKey) {
        List<CsvQuestionRowPayload> rows = parseRows(objectKey);
        int validRows = 0;
        for (CsvQuestionRowPayload row : rows) {
            if (row.valid()) validRows++;
        }
        return new CsvPreviewResponse(objectKey, rows.size(), validRows, rows.size() - validRows, rows);
    }

    public CsvImportSummary importDrafts(String objectKey) {
        List<CsvQuestionRowPayload> rows = parseRows(objectKey);
        int imported = 0;
        for (CsvQuestionRowPayload row : rows) {
            if (!row.valid()) {
                continue;
            }
            QuestionDraft draft = new QuestionDraft(
                    row.type(),
                    row.difficulty(),
                    row.workflowStatus(),
                    row.questionText(),
                    null,
                    null,
                    row.explanation(),
                    row.sourceReference(),
                    row.licenseCategory(),
                    row.options().stream().map(option -> new QuestionOption(option.key(), option.text(), null, null, option.correct())).toList());
            authoring.create(new CreateQuestionRequest(row.taxonomyNodeId(), row.actor(), draft));
            imported++;
        }
        return new CsvImportSummary(objectKey, imported, rows.size() - imported, rows);
    }

    private List<CsvQuestionRowPayload> parseRows(String objectKey) {
        if (!storage.exists(objectKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uploaded CSV was not found");
        }
        String csv = storage.readText(objectKey);
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreSurroundingSpaces(true)
                .build()
                .parse(new StringReader(csv))) {
            List<CsvQuestionRowPayload> rows = new ArrayList<>();
            int lineNumber = 1;
            for (CSVRecord record : parser) {
                rows.add(parseRecord(record, lineNumber++));
            }
            return rows;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to parse CSV", ex);
        }
    }

    private CsvQuestionRowPayload parseRecord(CSVRecord record, int lineNumber) {
        List<String> errors = new ArrayList<>();
        UUID taxonomyNodeId = parseUuid(record, "taxonomyNodeId", errors);
        String actor = text(record, "actor", errors);
        QuestionType type = parseEnum(record, "questionType", QuestionType.class, errors);
        Difficulty difficulty = parseEnum(record, "difficulty", Difficulty.class, errors);
        WorkflowStatus workflowStatus = parseEnum(record, "workflowStatus", WorkflowStatus.class, errors);
        String questionText = text(record, "questionText", errors);
        String explanation = optionalText(record, "explanation");
        String sourceReference = optionalText(record, "sourceReference");
        String licenseCategory = optionalText(record, "licenseCategory");
        List<CsvQuestionOptionsPayload> options = parseOptions(optionalText(record, "options"), errors);
        QuestionDraft draft = new QuestionDraft(type, difficulty, workflowStatus, questionText, null, null, explanation, sourceReference, licenseCategory,
                options.stream().map(option -> new QuestionOption(option.key(), option.text(), null, null, option.correct())).toList());
        errors.addAll(validator.validate(draft));
        return new CsvQuestionRowPayload(lineNumber, taxonomyNodeId, actor, type, difficulty, workflowStatus, questionText, explanation,
                sourceReference, licenseCategory, options, errors, errors.isEmpty());
    }

    private List<CsvQuestionOptionsPayload> parseOptions(String value, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add("options is required");
            return List.of();
        }
        List<CsvQuestionOptionsPayload> options = new ArrayList<>();
        String[] segments = value.split(";");
        for (String segment : segments) {
            String[] parts = segment.split("\\|", -1);
            if (parts.length < 3) {
                errors.add("options entry must be key|text|correct");
                continue;
            }
            boolean correct = Boolean.parseBoolean(parts[2].trim());
            options.add(new CsvQuestionOptionsPayload(parts[0].trim(), parts[1].trim(), correct));
        }
        return options;
    }

    private UUID parseUuid(CSVRecord record, String field, List<String> errors) {
        String value = optionalText(record, field);
        if (value == null || value.isBlank()) {
            errors.add(field + " is required");
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            errors.add(field + " must be a valid UUID");
            return null;
        }
    }

    private <E extends Enum<E>> E parseEnum(CSVRecord record, String field, Class<E> type, List<String> errors) {
        String value = optionalText(record, field);
        if (value == null || value.isBlank()) {
            errors.add(field + " is required");
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            errors.add(field + " is invalid");
            return null;
        }
    }

    private String text(CSVRecord record, String field, List<String> errors) {
        String value = optionalText(record, field);
        if (value == null || value.isBlank()) {
            errors.add(field + " is required");
            return null;
        }
        return value.trim();
    }

    private String optionalText(CSVRecord record, String field) {
        return record.isMapped(field) ? record.get(field) : null;
    }
}
