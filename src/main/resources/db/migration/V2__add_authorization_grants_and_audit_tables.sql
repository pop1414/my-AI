CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_bases_workspace_kb
ON knowledge_bases (workspace_id, kb_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_documents_workspace_document
ON ingest_documents (workspace_id, document_id);

CREATE TABLE IF NOT EXISTS knowledge_base_grants (
    grant_id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    kb_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_base_grants_workspace_kb_user
ON knowledge_base_grants (workspace_id, kb_id, user_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_base_grants_user
ON knowledge_base_grants (user_id, status);

CREATE TABLE IF NOT EXISTS document_grants (
    grant_id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    permission VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_document_grants_workspace_document_user
ON document_grants (workspace_id, document_id, user_id);

CREATE INDEX IF NOT EXISTS idx_document_grants_user
ON document_grants (user_id, status);

CREATE TABLE IF NOT EXISTS audit_events (
    audit_event_id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64),
    actor_user_id VARCHAR(64),
    actor_username VARCHAR(100),
    event_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(32),
    target_id VARCHAR(128),
    outcome VARCHAR(16) NOT NULL,
    reason VARCHAR(255) NOT NULL DEFAULT '',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_events_workspace_occurred_at
ON audit_events (workspace_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_events_actor_occurred_at
ON audit_events (actor_user_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_events_type_occurred_at
ON audit_events (event_type, occurred_at DESC);

ALTER TABLE knowledge_base_grants
    DROP CONSTRAINT IF EXISTS ck_knowledge_base_grants_role;
ALTER TABLE knowledge_base_grants
    ADD CONSTRAINT ck_knowledge_base_grants_role
        CHECK (role IN ('KB_MANAGER', 'KB_CONTRIBUTOR', 'KB_READER', 'KB_ASKER'));

ALTER TABLE knowledge_base_grants
    DROP CONSTRAINT IF EXISTS ck_knowledge_base_grants_status;
ALTER TABLE knowledge_base_grants
    ADD CONSTRAINT ck_knowledge_base_grants_status
        CHECK (status IN ('ACTIVE', 'DISABLED'));

ALTER TABLE knowledge_base_grants
    DROP CONSTRAINT IF EXISTS fk_knowledge_base_grants_workspace;
ALTER TABLE knowledge_base_grants
    ADD CONSTRAINT fk_knowledge_base_grants_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (workspace_id);

ALTER TABLE knowledge_base_grants
    DROP CONSTRAINT IF EXISTS fk_knowledge_base_grants_kb;
ALTER TABLE knowledge_base_grants
    ADD CONSTRAINT fk_knowledge_base_grants_kb
        FOREIGN KEY (workspace_id, kb_id) REFERENCES knowledge_bases (workspace_id, kb_id) ON DELETE CASCADE;

ALTER TABLE knowledge_base_grants
    DROP CONSTRAINT IF EXISTS fk_knowledge_base_grants_user;
ALTER TABLE knowledge_base_grants
    ADD CONSTRAINT fk_knowledge_base_grants_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

ALTER TABLE document_grants
    DROP CONSTRAINT IF EXISTS ck_document_grants_permission;
ALTER TABLE document_grants
    ADD CONSTRAINT ck_document_grants_permission
        CHECK (permission IN ('DOC_ALLOW_READ', 'DOC_ALLOW_MANAGE', 'DOC_DENY'));

ALTER TABLE document_grants
    DROP CONSTRAINT IF EXISTS ck_document_grants_status;
ALTER TABLE document_grants
    ADD CONSTRAINT ck_document_grants_status
        CHECK (status IN ('ACTIVE', 'DISABLED'));

ALTER TABLE document_grants
    DROP CONSTRAINT IF EXISTS fk_document_grants_workspace;
ALTER TABLE document_grants
    ADD CONSTRAINT fk_document_grants_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (workspace_id);

ALTER TABLE document_grants
    DROP CONSTRAINT IF EXISTS fk_document_grants_document;
ALTER TABLE document_grants
    ADD CONSTRAINT fk_document_grants_document
        FOREIGN KEY (workspace_id, document_id) REFERENCES ingest_documents (workspace_id, document_id) ON DELETE CASCADE;

ALTER TABLE document_grants
    DROP CONSTRAINT IF EXISTS fk_document_grants_user;
ALTER TABLE document_grants
    ADD CONSTRAINT fk_document_grants_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

ALTER TABLE audit_events
    DROP CONSTRAINT IF EXISTS ck_audit_events_outcome;
ALTER TABLE audit_events
    ADD CONSTRAINT ck_audit_events_outcome
        CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'));

ALTER TABLE audit_events
    DROP CONSTRAINT IF EXISTS fk_audit_events_workspace;
ALTER TABLE audit_events
    ADD CONSTRAINT fk_audit_events_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (workspace_id);

ALTER TABLE audit_events
    DROP CONSTRAINT IF EXISTS fk_audit_events_actor_user;
ALTER TABLE audit_events
    ADD CONSTRAINT fk_audit_events_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users (user_id) ON DELETE SET NULL;
