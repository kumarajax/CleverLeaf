package com.clearleaf.api;

import java.util.List;

public record QuestionCursorPage(
        List<QuestionAdminRecord> content,
        String nextCursor,
        boolean hasNext,
        int size) {
}
