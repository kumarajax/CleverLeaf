package com.clearleaf.api;

import java.util.List;
import java.util.UUID;

public record UpdateAdminAssignedTestQuestionsRequest(List<UUID> questionIds) {
}
