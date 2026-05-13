-- V5：为 document version chain 落地最小后端基线
--
-- 本迁移的目标是：
-- 1. 让 ingest_documents 从“单条记录承载全部版本事实”过渡为“document 主表 + latest projection”
-- 2. 新增 ingest_document_versions 承载版本级文件事实、处理事实与来源事实
-- 3. 为现有历史数据回填 version 1，保证迁移后旧 document 仍可被最新版本语义读取
--
-- 当前阶段只落基础版本链，不引入 upload new version / rollback 的业务接口与更多版本数据。

-- 为 ingest_documents 增加 latest projection 字段。
-- 这些字段用于表达系统当前认定的“最新版本头部信息”，
-- 让现有列表、状态查询和详情查询可以继续围绕 document 主表工作。
ALTER TABLE ingest_documents
    ADD COLUMN IF NOT EXISTS latest_version_number INT,
    ADD COLUMN IF NOT EXISTS latest_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS latest_filename VARCHAR(512),
    ADD COLUMN IF NOT EXISTS latest_version_origin_type VARCHAR(32);

-- 新增版本事实表 ingest_document_versions。
-- 该表承载版本级文件事实、处理状态、错误上下文与 processing_metadata，
-- 使 document 稳定身份与具体版本细节分离。
CREATE TABLE IF NOT EXISTS ingest_document_versions (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    version_number INT NOT NULL,
    version_origin_type VARCHAR(32) NOT NULL,
    rollback_from_version_number INT,
    file_hash VARCHAR(64) NOT NULL,
    filename VARCHAR(512) NOT NULL,
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
    processing_metadata JSONB,
    created_by_user_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- 同一 document 下，version_number 必须唯一。
CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_document_versions_document_version
ON ingest_document_versions (document_id, version_number);

-- 供按 document 拉取版本历史或定位 latest version 使用。
CREATE INDEX IF NOT EXISTS idx_ingest_document_versions_document
ON ingest_document_versions (document_id, version_number DESC);

-- 版本事实从属于 document 资产；删除 document 时级联删除其版本事实。
ALTER TABLE ingest_document_versions
    DROP CONSTRAINT IF EXISTS fk_ingest_document_versions_document;
ALTER TABLE ingest_document_versions
    ADD CONSTRAINT fk_ingest_document_versions_document
        FOREIGN KEY (document_id) REFERENCES ingest_documents (document_id) ON DELETE CASCADE;

-- 为存量 document 回填 version 1。
-- 当前历史数据尚未形成真正的多版本链，因此统一按：
-- - version_number = 1
-- - version_origin_type = 'UPLOAD'
-- 回填，确保迁移后旧数据可以被 latest version 查询正确命中。
INSERT INTO ingest_document_versions (
    document_id,
    version_number,
    version_origin_type,
    rollback_from_version_number,
    file_hash,
    filename,
    file_size,
    status,
    failure_reason,
    retry_count,
    retry_max,
    next_retry_at,
    last_error_code,
    last_error_message,
    last_error_at,
    reprocess_count,
    reprocess_requested_at,
    split_version,
    processing_metadata,
    created_at,
    updated_at
)
SELECT
    d.document_id,
    1,
    'UPLOAD',
    NULL,
    COALESCE(d.file_hash, ''),
    COALESCE(d.filename, d.document_id),
    d.file_size,
    d.status,
    d.failure_reason,
    d.retry_count,
    d.retry_max,
    d.next_retry_at,
    d.last_error_code,
    d.last_error_message,
    d.last_error_at,
    d.reprocess_count,
    d.reprocess_requested_at,
    d.split_version,
    d.processing_metadata,
    d.created_at,
    d.updated_at
FROM ingest_documents d
WHERE NOT EXISTS (
    SELECT 1
    FROM ingest_document_versions v
    WHERE v.document_id = d.document_id
      AND v.version_number = 1
);

-- 用旧单表字段回填 latest projection。
-- 在当前阶段，latest projection 与旧字段保持同值，
-- 这样现有查询路径仍能稳定工作，同时为后续真正多版本演进预留 seam。
UPDATE ingest_documents
SET latest_version_number = COALESCE(latest_version_number, 1),
    latest_status = COALESCE(latest_status, status),
    latest_filename = COALESCE(latest_filename, filename),
    latest_version_origin_type = COALESCE(latest_version_origin_type, 'UPLOAD');

-- latest_version_number / latest_status / latest_version_origin_type
-- 是后续 latest projection 的核心字段，迁移完成后应为必填。
ALTER TABLE ingest_documents
    ALTER COLUMN latest_version_number SET NOT NULL;

ALTER TABLE ingest_documents
    ALTER COLUMN latest_status SET NOT NULL;

ALTER TABLE ingest_documents
    ALTER COLUMN latest_version_origin_type SET NOT NULL;
