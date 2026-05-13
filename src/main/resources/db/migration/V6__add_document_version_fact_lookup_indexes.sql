-- V6：为版本事实读路径补充查询索引
--
-- 上传幂等等读路径已经从 ingest_documents.file_hash 切换到
-- ingest_document_versions.file_hash。该索引保证版本事实表成为文件哈希事实源后，
-- 查询无需依赖主表旧版本事实列。
CREATE INDEX IF NOT EXISTS idx_ingest_document_versions_file_hash
ON ingest_document_versions (file_hash);
