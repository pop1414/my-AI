package io.github.spike.myai.auth.interfaces.rest.dto;

import java.util.List;

public record ReplaceMemberDocumentGrantsRequest(List<Assignment> assignments) {
    public record Assignment(String documentId, String permission) {
    }
}
