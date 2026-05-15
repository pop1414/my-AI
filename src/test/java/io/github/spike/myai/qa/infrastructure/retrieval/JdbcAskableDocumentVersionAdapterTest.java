package io.github.spike.myai.qa.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JdbcAskableDocumentVersionAdapter 读边界测试。
 */
@SpringBootTest(properties = "myai.ingest.worker.enabled=false")
class JdbcAskableDocumentVersionAdapterTest {

    @Autowired
    private JdbcAskableDocumentVersionAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String kbId;
    private String userId;

    @BeforeEach
    void cleanBeforeTest() {
        cleanQaFixtures();
    }

    @AfterEach
    void cleanUp() {
        cleanQaFixtures();
    }

    @Test
    @DisplayName("可问答版本查询应执行授权边界并回退到最近 INDEXED 版本")
    void findAskableVersionsForQuestion_shouldApplyAuthorizationAndFallbackToLatestIndexedVersion() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        kbId = "kb-qa-" + suffix;
        userId = "user-qa-" + suffix;
        String askableDocId = "doc-qa-askable-" + suffix;
        String deniedDocId = "doc-qa-deny-" + suffix;
        String noIndexedDocId = "doc-qa-no-index-" + suffix;
        String deletedDocId = "doc-qa-deleted-" + suffix;
        Instant now = Instant.parse("2026-05-14T10:00:00Z");

        insertUser(userId, "qa-review-" + suffix, now);
        insertKnowledgeBase(kbId, now);
        insertKnowledgeBaseGrant(kbId, userId, now);
        insertDocument(askableDocId, kbId, 3, "FAILED", "doc-v3.pdf", now);
        insertVersion(askableDocId, 1, "INDEXED", "doc-v1.pdf", now.minusSeconds(300));
        insertVersion(askableDocId, 2, "INDEXED", "doc-v2.pdf", now.minusSeconds(120));
        insertVersion(askableDocId, 3, "FAILED", "doc-v3.pdf", now);
        insertDocument(deniedDocId, kbId, 1, "INDEXED", "deny-v1.pdf", now);
        insertVersion(deniedDocId, 1, "INDEXED", "deny-v1.pdf", now);
        insertDocumentGrant(deniedDocId, userId, "DOC_DENY", now);
        insertDocument(noIndexedDocId, kbId, 2, "FAILED", "failed-v2.pdf", now);
        insertVersion(noIndexedDocId, 1, "FAILED", "failed-v1.pdf", now.minusSeconds(60));
        insertVersion(noIndexedDocId, 2, "FAILED", "failed-v2.pdf", now);
        insertDocument(deletedDocId, kbId, 2, "DELETED", "deleted-v2.pdf", now);
        insertVersion(deletedDocId, 1, "INDEXED", "deleted-v1.pdf", now.minusSeconds(60));
        insertVersion(deletedDocId, 2, "DELETED", "deleted-v2.pdf", now);

        var result = adapter.findAskableVersionsForQuestion(
                new CurrentUser(userId, "qa-review-" + suffix, "default", WorkspaceRole.WORKSPACE_MEMBER),
                kbId);

        assertEquals(1, result.size());
        assertEquals(askableDocId, result.getFirst().documentId());
        assertEquals(3, result.getFirst().latestVersionNumber());
        assertEquals(2, result.getFirst().askableVersionNumber());
        assertEquals("doc-v2.pdf", result.getFirst().sourceFilename());
        assertEquals(now.minusSeconds(120), result.getFirst().sourceUpdatedAt());
    }

    private void insertUser(String userId, String username, Instant now) {
        jdbcTemplate.update(
                "INSERT INTO users (user_id, username, display_name, status, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                userId,
                username,
                username,
                timestamp(now),
                timestamp(now));
        jdbcTemplate.update(
                "INSERT INTO workspace_memberships (workspace_id, user_id, role, status, created_at, updated_at) VALUES ('default', ?, 'WORKSPACE_MEMBER', 'ACTIVE', ?, ?)",
                userId,
                timestamp(now),
                timestamp(now));
    }

    private void insertKnowledgeBase(String kbId, Instant now) {
        jdbcTemplate.update(
                "INSERT INTO knowledge_bases (kb_id, workspace_id, name, description, status, created_at, updated_at) VALUES (?, 'default', ?, '', 'ACTIVE', ?, ?)",
                kbId,
                kbId,
                timestamp(now),
                timestamp(now));
    }

    private void insertKnowledgeBaseGrant(String kbId, String userId, Instant now) {
        jdbcTemplate.update(
                "INSERT INTO knowledge_base_grants (workspace_id, kb_id, user_id, role, status, created_at, updated_at) VALUES ('default', ?, ?, 'KB_ASKER', 'ACTIVE', ?, ?)",
                kbId,
                userId,
                timestamp(now),
                timestamp(now));
    }

    private void insertDocument(String documentId, String kbId, int latestVersionNumber, String latestStatus, String filename, Instant now) {
        jdbcTemplate.update(
                """
                        INSERT INTO ingest_documents
                          (document_id, workspace_id, kb_id, file_hash, filename, file_size, status,
                           latest_version_number, latest_status, latest_filename, latest_version_origin_type,
                           retry_count, retry_max, reprocess_count, split_version, created_at, updated_at)
                        VALUES (?, 'default', ?, ?, ?, 100, ?, ?, ?, ?, 'UPLOAD', 0, 3, 0, ?, ?, ?)
                        """,
                documentId,
                kbId,
                "hash-" + documentId,
                filename,
                latestStatus,
                latestVersionNumber,
                latestStatus,
                filename,
                "version-" + latestVersionNumber + "-v1",
                timestamp(now),
                timestamp(now));
    }

    private void insertVersion(String documentId, int versionNumber, String status, String filename, Instant updatedAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO ingest_document_versions
                          (document_id, version_number, version_origin_type, file_hash, filename, file_size, status,
                           retry_count, retry_max, reprocess_count, split_version, created_at, updated_at)
                        VALUES (?, ?, 'UPLOAD', ?, ?, 100, ?, 0, 3, 0, ?, ?, ?)
                        """,
                documentId,
                versionNumber,
                "hash-" + documentId + "-" + versionNumber,
                filename,
                status,
                "version-" + versionNumber + "-v1",
                timestamp(updatedAt),
                timestamp(updatedAt));
    }

    private void insertDocumentGrant(String documentId, String userId, String permission, Instant now) {
        jdbcTemplate.update(
                "INSERT INTO document_grants (workspace_id, document_id, user_id, permission, status, created_at, updated_at) VALUES ('default', ?, ?, ?, 'ACTIVE', ?, ?)",
                documentId,
                userId,
                permission,
                timestamp(now),
                timestamp(now));
    }

    private void cleanQaFixtures() {
        jdbcTemplate.update("DELETE FROM document_grants WHERE document_id LIKE 'doc-qa-%' OR user_id LIKE 'user-qa-%'");
        jdbcTemplate.update("DELETE FROM knowledge_base_grants WHERE kb_id LIKE 'kb-qa-%' OR user_id LIKE 'user-qa-%'");
        jdbcTemplate.update("DELETE FROM ingest_document_versions WHERE document_id LIKE 'doc-qa-%'");
        jdbcTemplate.update("DELETE FROM ingest_documents WHERE document_id LIKE 'doc-qa-%'");
        jdbcTemplate.update("DELETE FROM knowledge_bases WHERE workspace_id = 'default' AND kb_id LIKE 'kb-qa-%'");
        jdbcTemplate.update("DELETE FROM workspace_memberships WHERE workspace_id = 'default' AND user_id LIKE 'user-qa-%'");
        jdbcTemplate.update("DELETE FROM users WHERE user_id LIKE 'user-qa-%'");
        kbId = null;
        userId = null;
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
