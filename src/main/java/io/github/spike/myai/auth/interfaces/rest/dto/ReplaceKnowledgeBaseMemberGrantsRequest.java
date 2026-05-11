package io.github.spike.myai.auth.interfaces.rest.dto;

import java.util.List;

public record ReplaceKnowledgeBaseMemberGrantsRequest(List<Assignment> assignments) {
    public record Assignment(String userId, String role) {
    }
}
