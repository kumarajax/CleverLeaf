package com.clearleaf.api;

import java.util.List;

public record GeneratedQuestionBatch(
        boolean chunkUseful,
        String skipReason,
        List<GeneratedQuestionDraft> questions) {
}
