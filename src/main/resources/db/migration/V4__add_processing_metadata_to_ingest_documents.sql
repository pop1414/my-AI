ALTER TABLE ingest_documents
    ADD COLUMN IF NOT EXISTS processing_metadata JSONB;
