-- V7：提炼 latest projection maintenance seam
--
-- 背景：
-- 1. 调用方通常只想推进一个 document version；
-- 2. 旧实现却在 JdbcDocumentRepository 中分散维护 ingest_documents 与
--    ingest_document_versions 之间的 latest projection 双写；
-- 3. 该迁移将首批高频路径收口到数据库侧统一 function，降低 drift 风险。

-- 统一收口“追加新 latest version”动作。
-- 当前覆盖上传新版本与回退新版本两类路径，并继续维护迁移期旧兼容镜像列。
CREATE OR REPLACE FUNCTION ingest_append_document_latest_version(
    p_workspace_id VARCHAR,
    p_document_id VARCHAR,
    p_expected_latest_version_number INT,
    p_version_number INT,
    p_version_origin_type VARCHAR,
    p_rollback_from_version_number INT,
    p_file_hash VARCHAR,
    p_filename VARCHAR,
    p_file_size BIGINT,
    p_status VARCHAR,
    p_failure_reason TEXT,
    p_retry_count INT,
    p_retry_max INT,
    p_next_retry_at TIMESTAMPTZ,
    p_last_error_code VARCHAR,
    p_last_error_message TEXT,
    p_last_error_at TIMESTAMPTZ,
    p_reprocess_count INT,
    p_reprocess_requested_at TIMESTAMPTZ,
    p_split_version VARCHAR,
    p_processing_metadata JSONB,
    p_created_by_user_id VARCHAR,
    p_created_at TIMESTAMPTZ,
    p_updated_at TIMESTAMPTZ
) RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_updated_rows INT;
BEGIN
    UPDATE ingest_documents
    SET file_hash = p_file_hash,
        filename = p_filename,
        file_size = p_file_size,
        status = p_status,
        latest_version_number = p_version_number,
        latest_status = p_status,
        latest_filename = p_filename,
        latest_version_origin_type = p_version_origin_type,
        failure_reason = p_failure_reason,
        retry_count = p_retry_count,
        retry_max = p_retry_max,
        next_retry_at = p_next_retry_at,
        last_error_code = p_last_error_code,
        last_error_message = p_last_error_message,
        last_error_at = p_last_error_at,
        reprocess_count = p_reprocess_count,
        reprocess_requested_at = p_reprocess_requested_at,
        split_version = p_split_version,
        processing_metadata = p_processing_metadata,
        updated_at = p_updated_at
    WHERE workspace_id = p_workspace_id
      AND document_id = p_document_id
      AND latest_version_number = p_expected_latest_version_number
      AND latest_status IN ('INDEXED', 'FAILED');

    GET DIAGNOSTICS v_updated_rows = ROW_COUNT;
    IF v_updated_rows <> 1 THEN
        RETURN FALSE;
    END IF;

    INSERT INTO ingest_document_versions
      (document_id, version_number, version_origin_type, rollback_from_version_number,
       file_hash, filename, file_size, status, failure_reason,
       retry_count, retry_max, next_retry_at, last_error_code, last_error_message, last_error_at,
       reprocess_count, reprocess_requested_at, split_version, processing_metadata,
       created_by_user_id, created_at, updated_at)
    VALUES (p_document_id, p_version_number, p_version_origin_type, p_rollback_from_version_number,
            p_file_hash, p_filename, p_file_size, p_status, p_failure_reason,
            p_retry_count, p_retry_max, p_next_retry_at, p_last_error_code, p_last_error_message, p_last_error_at,
            p_reprocess_count, p_reprocess_requested_at, p_split_version, p_processing_metadata,
            p_created_by_user_id, p_created_at, p_updated_at)
    ON CONFLICT (document_id, version_number) DO UPDATE SET
      version_origin_type = EXCLUDED.version_origin_type,
      rollback_from_version_number = EXCLUDED.rollback_from_version_number,
      file_hash = EXCLUDED.file_hash,
      filename = EXCLUDED.filename,
      file_size = EXCLUDED.file_size,
      status = EXCLUDED.status,
      failure_reason = EXCLUDED.failure_reason,
      retry_count = EXCLUDED.retry_count,
      retry_max = EXCLUDED.retry_max,
      next_retry_at = EXCLUDED.next_retry_at,
      last_error_code = EXCLUDED.last_error_code,
      last_error_message = EXCLUDED.last_error_message,
      last_error_at = EXCLUDED.last_error_at,
      reprocess_count = EXCLUDED.reprocess_count,
      reprocess_requested_at = EXCLUDED.reprocess_requested_at,
      split_version = EXCLUDED.split_version,
      processing_metadata = EXCLUDED.processing_metadata,
      created_by_user_id = EXCLUDED.created_by_user_id,
      created_at = EXCLUDED.created_at,
      updated_at = EXCLUDED.updated_at;

    RETURN TRUE;
END;
$$;

-- 统一收口 latest version 的处理状态推进。
-- 首批覆盖 markIndexed / markFailed / markRetry 三条高频处理链路。
CREATE OR REPLACE FUNCTION ingest_update_latest_document_version_processing(
    p_workspace_id VARCHAR,
    p_document_id VARCHAR,
    p_expected_status VARCHAR,
    p_target_status VARCHAR,
    p_failure_reason TEXT,
    p_retry_count INT,
    p_next_retry_at TIMESTAMPTZ,
    p_processing_metadata JSONB,
    p_last_error_code VARCHAR,
    p_last_error_message TEXT,
    p_last_error_at TIMESTAMPTZ,
    p_updated_at TIMESTAMPTZ
) RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_updated_rows INT;
    v_latest_version_number INT;
BEGIN
    SELECT latest_version_number
    INTO v_latest_version_number
    FROM ingest_documents
    WHERE workspace_id = p_workspace_id
      AND document_id = p_document_id
      AND latest_status = p_expected_status
    FOR UPDATE;

    IF v_latest_version_number IS NULL THEN
        RETURN FALSE;
    END IF;

    IF p_target_status = 'INDEXED' THEN
        UPDATE ingest_documents
        SET status = 'INDEXED',
            latest_status = 'INDEXED',
            failure_reason = NULL,
            retry_count = 0,
            next_retry_at = NULL,
            last_error_code = NULL,
            last_error_message = NULL,
            last_error_at = NULL,
            processing_metadata = p_processing_metadata,
            updated_at = p_updated_at
        WHERE workspace_id = p_workspace_id
          AND document_id = p_document_id
          AND latest_status = p_expected_status;

        GET DIAGNOSTICS v_updated_rows = ROW_COUNT;
        IF v_updated_rows <> 1 THEN
            RETURN FALSE;
        END IF;

        UPDATE ingest_document_versions
        SET status = 'INDEXED',
            failure_reason = NULL,
            retry_count = 0,
            next_retry_at = NULL,
            last_error_code = NULL,
            last_error_message = NULL,
            last_error_at = NULL,
            processing_metadata = p_processing_metadata,
            updated_at = p_updated_at
        WHERE document_id = p_document_id
          AND version_number = v_latest_version_number;
        RETURN TRUE;
    END IF;

    IF p_target_status = 'FAILED' THEN
        UPDATE ingest_documents
        SET status = 'FAILED',
            latest_status = 'FAILED',
            failure_reason = p_failure_reason,
            processing_metadata = p_processing_metadata,
            last_error_code = p_last_error_code,
            last_error_message = p_last_error_message,
            last_error_at = p_last_error_at,
            updated_at = p_updated_at
        WHERE workspace_id = p_workspace_id
          AND document_id = p_document_id
          AND latest_status = p_expected_status;

        GET DIAGNOSTICS v_updated_rows = ROW_COUNT;
        IF v_updated_rows <> 1 THEN
            RETURN FALSE;
        END IF;

        UPDATE ingest_document_versions
        SET status = 'FAILED',
            failure_reason = p_failure_reason,
            processing_metadata = p_processing_metadata,
            last_error_code = p_last_error_code,
            last_error_message = p_last_error_message,
            last_error_at = p_last_error_at,
            updated_at = p_updated_at
        WHERE document_id = p_document_id
          AND version_number = v_latest_version_number;
        RETURN TRUE;
    END IF;

    IF p_target_status = 'UPLOADED' THEN
        UPDATE ingest_documents
        SET status = 'UPLOADED',
            latest_status = 'UPLOADED',
            failure_reason = NULL,
            retry_count = p_retry_count,
            next_retry_at = p_next_retry_at,
            processing_metadata = NULL,
            last_error_code = p_last_error_code,
            last_error_message = p_last_error_message,
            last_error_at = p_last_error_at,
            updated_at = p_updated_at
        WHERE workspace_id = p_workspace_id
          AND document_id = p_document_id
          AND latest_status = p_expected_status;

        GET DIAGNOSTICS v_updated_rows = ROW_COUNT;
        IF v_updated_rows <> 1 THEN
            RETURN FALSE;
        END IF;

        UPDATE ingest_document_versions
        SET status = 'UPLOADED',
            failure_reason = NULL,
            retry_count = p_retry_count,
            next_retry_at = p_next_retry_at,
            processing_metadata = NULL,
            last_error_code = p_last_error_code,
            last_error_message = p_last_error_message,
            last_error_at = p_last_error_at,
            updated_at = p_updated_at
        WHERE document_id = p_document_id
          AND version_number = v_latest_version_number;
        RETURN TRUE;
    END IF;

    RAISE EXCEPTION 'unsupported latest processing target status: %', p_target_status;
END;
$$;
