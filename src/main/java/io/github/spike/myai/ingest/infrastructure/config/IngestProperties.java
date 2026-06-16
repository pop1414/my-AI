package io.github.spike.myai.ingest.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ingest 管道配置属性（Configuration Properties）。
 *
 * <p>绑定 {@code myai.ingest} 前缀的 YAML 配置，按功能域拆分为内嵌配置类：
 * <ul>
 *   <li>{@link Parser}：Tika 解析参数</li>
 *   <li>{@link Storage}：源文件与中间产物存储参数</li>
 *   <li>{@link S3}：S3 兼容对象存储连接参数</li>
 *   <li>{@link Artifacts}：调试产物保留策略</li>
 *   <li>{@link Chunk}：文本分块参数</li>
 *   <li>{@link Worker}：异步 worker 调度参数</li>
 *   <li>{@link SchemaCheck}：数据库 schema 自检开关</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
@Getter
@ConfigurationProperties(prefix = "myai.ingest")
public class IngestProperties {

    private final Parser parser = new Parser();
    private final Storage storage = new Storage();
    private final Chunk chunk = new Chunk();
    private final Worker worker = new Worker();
    private final SchemaCheck schemaCheck = new SchemaCheck();

    /**
     * Tika 解析器参数配置。
     */
    @Setter
    @Getter
    public static class Parser {
        /** 解析文本最大长度限制（字符数），默认 200 万字符，超过将触发异常 */
        private int maxTextLength = 2000000;
        /** 是否解析嵌入资源（如 Excel 中的嵌入 PDF），默认关闭以节省内存 */
        private boolean parseEmbeddedResource = false;

    }

    /**
     * 文件存储参数配置。
     */
    @Setter
    @Getter
    public static class Storage {
        /** 存储介质类型，默认使用本地文件系统 */
        private StorageType type = StorageType.LOCAL;
        /** 文件存储根目录，源文件和中间产物均在此目录下按 documentId 分目录存放 */
        private String rootDir = "data/ingest";
        /** S3 兼容对象存储连接配置 */
        private final S3 s3 = new S3();
        /** 中间产物保留策略配置 */
        private final Artifacts artifacts = new Artifacts();

    }

    /**
     * 文档资产存储介质类型。
     */
    public enum StorageType {
        /** 本地文件系统存储 */
        LOCAL,
        /** S3 兼容对象存储 */
        S3
    }

    /**
     * S3 兼容对象存储连接参数。
     *
     * <p>该配置只描述标准 S3 访问参数，不绑定 RustFS 产品名。应用进入 {@code s3}
     * 存储模式时，基础设施层使用这些参数创建 S3 client。
     */
    @Setter
    @Getter
    public static class S3 {
        /** S3 兼容服务端点，例如 http://localhost:9000 */
        private String endpoint = "";
        /** 文档资产 bucket 名称 */
        private String bucket = "myai-documents";
        /** S3 region，RustFS 本地验证默认使用 us-east-1 */
        private String region = "us-east-1";
        /** S3 access key */
        private String accessKey = "";
        /** S3 secret key */
        private String secretKey = "";
        /** 是否启用 path-style access，本地 RustFS 默认需要开启 */
        private boolean pathStyleAccess = true;

    }

    /**
     * 中间产物保留策略。
     *
     * <p>控制处理链路中调试产物的文件化保留行为：
     * <ul>
     *   <li>{@code keepParseResultJson}：是否保留 processing_metadata 文件化载体（默认 true）</li>
     * </ul>
     *
     * <p>注意：cleaned.md 作为主链产物强制写入，不受此配置控制。
     */
    @Setter
    @Getter
    public static class Artifacts {
        /** 正文读取允许的最大 artifact 字节数，超过时拒绝返回完整正文 */
        private long maxReadBytes = 2_000_000L;
        private boolean keepParseResultJson = true;

    }

    /**
     * 文本分块参数配置。
     */
    @Setter
    @Getter
    public static class Chunk {
        /** 单个 chunk 的目标大小（字符数），默认 500 */
        private int chunkSize = 500;
        /** chunk 之间的重叠大小（字符数），默认 100，用于保持语义连续性 */
        private int overlapSize = 100;

    }

    /**
     * 异步 Worker 调度参数配置。
     */
    @Setter
    @Getter
    public static class Worker {
        /** 是否启用异步 Worker 轮询处理，默认关闭 */
        private boolean enabled = false;
        /** Worker 轮询间隔（毫秒），默认 5 秒 */
        private long pollDelayMs = 5000L;

    }

    /**
     * 数据库 Schema 自检参数配置。
     */
    @Setter
    @Getter
    public static class SchemaCheck {
        /** 是否在启动时执行数据库 schema 自检，默认开启 */
        private boolean enabled = true;

    }
}
