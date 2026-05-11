package io.github.spike.myai.auth.interfaces.rest.dto;

import java.util.List;

public record ReplaceMemberKnowledgeBaseGrantsRequest(List<Assignment> assignments) {
    public record Assignment(String kbId, String role) {
    }
}
