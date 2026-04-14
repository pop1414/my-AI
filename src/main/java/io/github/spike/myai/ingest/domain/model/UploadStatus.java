package io.github.spike.myai.ingest.domain.model;

/**
 * 上传状态（领域枚举）。
 *
 * <p>状态语义：
 * <ul>
 *     <li>ACCEPTED：上传请求已被系统受理（接口响应语义）。</li>
 *     <li>UPLOADED：文档元数据已落库，等待进入处理链路。</li>
 *     <li>INGESTING：正在执行解析、分块、向量化与入库。</li>
 *     <li>INDEXED：向量索引已完成，可进入问答检索。</li>
 *     <li>FAILED：处理失败，通常需要结合失败原因定位问题。</li>
 *     <li>DELETING：文档资产删除中（正在清理源文件与向量数据）。</li>
 *     <li>DELETED：文档资产已删除（保留元数据用于审计与状态可观测）。</li>
 * </ul>
 */
public enum UploadStatus {
    ACCEPTED,
    UPLOADED,
    INGESTING,
    INDEXED,
    FAILED,
    DELETING,
    DELETED
}
