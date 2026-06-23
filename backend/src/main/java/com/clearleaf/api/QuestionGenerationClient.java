package com.clearleaf.api;

public interface QuestionGenerationClient {
    GeneratedQuestionBatch generate(QuestionGenerationRequest request, AiProviderCredentials credentials);
}
