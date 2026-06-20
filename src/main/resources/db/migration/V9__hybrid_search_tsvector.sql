-- V9__hybrid_search_tsvector.sql
-- 添加 tsvector 生成列和 GIN 索引，为 BM25 稀疏检索提供基础设施

ALTER TABLE vector_store
    ADD COLUMN content_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;

CREATE INDEX idx_vector_store_fts
    ON vector_store USING GIN (content_tsv);
