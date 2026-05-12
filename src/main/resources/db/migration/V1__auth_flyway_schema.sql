CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS workspaces (
    workspace_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO workspaces (workspace_id, name, description, status, created_at, updated_at)
SELECT 'default', 'default', '', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM workspaces WHERE workspace_id = 'default'
);

CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username
ON users (username);

CREATE TABLE IF NOT EXISTS local_credentials (
    user_id VARCHAR(64) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    password_algo VARCHAR(32) NOT NULL DEFAULT 'bcrypt',
    password_updated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS workspace_memberships (
    membership_id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_memberships_workspace_user
ON workspace_memberships (workspace_id, user_id);

CREATE TABLE IF NOT EXISTS login_lock_states (
    user_id VARCHAR(64) PRIMARY KEY,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_failed_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id BIGSERIAL PRIMARY KEY,
    kb_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE knowledge_bases
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_bases_kb_id
ON knowledge_bases (kb_id);

INSERT INTO knowledge_bases (kb_id, workspace_id, name, description, status, created_at, updated_at)
SELECT 'default', 'default', 'default', '', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_bases WHERE kb_id = 'default'
);

CREATE TABLE IF NOT EXISTS ingest_documents (
    document_id VARCHAR(64) PRIMARY KEY,
    kb_id VARCHAR(128) NOT NULL,
    workspace_id VARCHAR(64),
    file_hash VARCHAR(64),
    filename VARCHAR(512),
    file_size BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_reason TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    retry_max INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    last_error_message TEXT,
    last_error_at TIMESTAMPTZ,
    reprocess_count INT NOT NULL DEFAULT 0,
    reprocess_requested_at TIMESTAMPTZ,
    split_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE ingest_documents
    ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(64);

ALTER TABLE ingest_documents
    ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS retry_max INT NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS last_error_message TEXT,
    ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reprocess_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reprocess_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS split_version VARCHAR(32) NOT NULL DEFAULT 'v1';

UPDATE ingest_documents
SET workspace_id = 'default'
WHERE workspace_id IS NULL OR workspace_id = '';

INSERT INTO knowledge_bases (kb_id, workspace_id, name, description, status, created_at, updated_at)
SELECT source.kb_id, 'default', source.kb_id, '', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT kb_id
    FROM ingest_documents
    WHERE kb_id IS NOT NULL AND kb_id <> ''
) source
WHERE NOT EXISTS (
    SELECT 1
    FROM knowledge_bases kb
    WHERE kb.kb_id = source.kb_id
);

UPDATE knowledge_bases
SET workspace_id = 'default'
WHERE workspace_id IS NULL OR workspace_id = '';

DROP INDEX IF EXISTS uk_ingest_documents_kb_file_hash;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_documents_kb_file_hash
ON ingest_documents (kb_id, file_hash)
WHERE file_hash IS NOT NULL AND status <> 'DELETED';

CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(1024)
);

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
ON vector_store USING HNSW (embedding vector_cosine_ops);

ALTER TABLE local_credentials
    DROP CONSTRAINT IF EXISTS fk_local_credentials_user;
ALTER TABLE local_credentials
    ADD CONSTRAINT fk_local_credentials_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

ALTER TABLE workspace_memberships
    DROP CONSTRAINT IF EXISTS fk_workspace_memberships_workspace;
ALTER TABLE workspace_memberships
    ADD CONSTRAINT fk_workspace_memberships_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (workspace_id);
ALTER TABLE workspace_memberships
    DROP CONSTRAINT IF EXISTS fk_workspace_memberships_user;
ALTER TABLE workspace_memberships
    ADD CONSTRAINT fk_workspace_memberships_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

ALTER TABLE login_lock_states
    DROP CONSTRAINT IF EXISTS fk_login_lock_states_user;
ALTER TABLE login_lock_states
    ADD CONSTRAINT fk_login_lock_states_user
        FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

ALTER TABLE knowledge_bases
    ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE knowledge_bases
    DROP CONSTRAINT IF EXISTS fk_knowledge_bases_workspace;
ALTER TABLE knowledge_bases
    ADD CONSTRAINT fk_knowledge_bases_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (workspace_id);

ALTER TABLE ingest_documents
    ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE ingest_documents
    DROP CONSTRAINT IF EXISTS fk_ingest_documents_workspace;
ALTER TABLE ingest_documents
    ADD CONSTRAINT fk_ingest_documents_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (workspace_id);
