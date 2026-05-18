ALTER TABLE knowledge_bases
    DROP CONSTRAINT IF EXISTS ck_knowledge_bases_status;

ALTER TABLE knowledge_bases
    ADD CONSTRAINT ck_knowledge_bases_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'));
