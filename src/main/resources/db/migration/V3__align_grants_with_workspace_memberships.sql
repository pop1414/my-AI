ALTER TABLE knowledge_base_grants
    DROP CONSTRAINT IF EXISTS fk_knowledge_base_grants_user;
ALTER TABLE knowledge_base_grants
    DROP CONSTRAINT IF EXISTS fk_knowledge_base_grants_membership;
ALTER TABLE knowledge_base_grants
    ADD CONSTRAINT fk_knowledge_base_grants_membership
        FOREIGN KEY (workspace_id, user_id) REFERENCES workspace_memberships (workspace_id, user_id) ON DELETE CASCADE;

ALTER TABLE document_grants
    DROP CONSTRAINT IF EXISTS fk_document_grants_user;
ALTER TABLE document_grants
    DROP CONSTRAINT IF EXISTS fk_document_grants_membership;
ALTER TABLE document_grants
    ADD CONSTRAINT fk_document_grants_membership
        FOREIGN KEY (workspace_id, user_id) REFERENCES workspace_memberships (workspace_id, user_id) ON DELETE CASCADE;
