package com.clearleaf.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class OpenAiQuestionGenerationClient implements QuestionGenerationClient {
    private final ObjectMapper objectMapper;
    private final String defaultBaseUrl;

    public OpenAiQuestionGenerationClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.openai.base-url}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.defaultBaseUrl = baseUrl;
    }

    @Override
    public GeneratedQuestionBatch generate(QuestionGenerationRequest request, AiProviderCredentials credentials) {
        if (credentials == null || credentials.apiKey() == null || credentials.apiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI provider connection is not configured");
        }
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(credentials.baseUrl() == null || credentials.baseUrl().isBlank() ? defaultBaseUrl : credentials.baseUrl())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            Map<String, Object> body = Map.of(
                    "model", credentials.model(),
                    "input", List.of(Map.of(
                            "role", "user",
                            "content", List.of(Map.of(
                                    "type", "input_text",
                                    "text", prompt(request))))),
                    "text", Map.of("format", Map.of(
                            "type", "json_schema",
                            "name", "cleverleaf_question_batch",
                            "strict", true,
                            "schema", schema())));
            String raw = restClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.apiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            String outputText = outputText(raw);
            return objectMapper.readValue(outputText, GeneratedQuestionBatch.class);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, openAiErrorMessage(ex), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to generate questions: " + safeMessage(ex), ex);
        }
    }

    private String openAiErrorMessage(RestClientResponseException ex) {
        String message = null;
        try {
            JsonNode root = objectMapper.readTree(ex.getResponseBodyAsString());
            message = root.path("error").path("message").asText(null);
        } catch (Exception ignored) {
            message = null;
        }
        if (message == null || message.isBlank()) {
            message = ex.getResponseBodyAsString();
        }
        if (message == null || message.isBlank()) {
            message = ex.getStatusText();
        }
        return "OpenAI request failed (" + ex.getStatusCode().value() + "): " + truncate(message);
    }

    private String safeMessage(Exception ex) {
        return truncate(ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage());
    }

    private String truncate(String message) {
        if (message == null) return "";
        return message.length() <= 500 ? message : message.substring(0, 500) + "...";
    }

    private String outputText(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) continue;
                for (JsonNode part : content) {
                    String text = part.path("text").asText(null);
                    if (text != null && !text.isBlank()) return text;
                }
            }
        }
        String direct = root.path("output_text").asText(null);
        if (direct != null && !direct.isBlank()) return direct;
        throw new IllegalStateException("OpenAI response did not include output text");
    }

    private String prompt(QuestionGenerationRequest request) {
        return """
                You generate CleverLeaf assessment questions from source text.
                Use only the provided source chunk. Do not use outside knowledge.
                Return zero questions if the chunk does not contain useful testable educational content.
                The taxonomy identifiers are deterministic and must be echoed exactly.

                taxonomyKey: %s
                childNodeKey: %s
                taxonomyPath: %s
                topic: %s
                instructions: %s
                requestedQuestionCount: %d
                allowedQuestionTypes: %s
                difficultyMix: %s
                sourceReference: %s

                Source chunk:
                %s
                """.formatted(
                request.taxonomyKey(),
                request.childNodeKey(),
                request.taxonomyPath(),
                request.topic(),
                request.instructions() == null ? "" : request.instructions(),
                request.requestedQuestionCount(),
                request.allowedQuestionTypes(),
                request.difficultyMix(),
                request.sourceReference(),
                request.chunkText());
    }

    private Map<String, Object> schema() {
        Map<String, Object> option = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("key", "text", "correct"),
                "properties", Map.of(
                        "key", Map.of("type", "string"),
                        "text", Map.of("type", "string"),
                        "correct", Map.of("type", "boolean")));
        Map<String, Object> question = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("taxonomyKey", "childNodeKey", "questionType", "difficulty", "questionText",
                        "options", "correctOptionKeys", "explanation", "sourceReference"),
                "properties", Map.of(
                        "taxonomyKey", Map.of("type", "string"),
                        "childNodeKey", Map.of("type", "string"),
                        "questionType", Map.of("type", "string", "enum", List.of("SINGLE_SELECT", "MULTIPLE_SELECT")),
                        "difficulty", Map.of("type", "string", "enum", List.of("EASY", "MEDIUM", "HARD")),
                        "questionText", Map.of("type", "string"),
                        "options", Map.of("type", "array", "minItems", 4, "maxItems", 6, "items", option),
                        "correctOptionKeys", Map.of("type", "array", "items", Map.of("type", "string")),
                        "explanation", Map.of("type", "string"),
                        "sourceReference", Map.of("type", "string")));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("chunkUseful", "skipReason", "questions"),
                "properties", Map.of(
                        "chunkUseful", Map.of("type", "boolean"),
                        "skipReason", Map.of("type", List.of("string", "null")),
                        "questions", Map.of("type", "array", "items", question)));
    }
}
