package io.github.spike.myai.knowledge.domain.port;

/**
 * 知识库业务键生成器（Domain Port / ID Generation Strategy）。
 *
 * <p>该接口是六边形架构中的<b>领域端口</b>，定义知识库全局唯一标识的生成契约。
 * 将 ID 生成策略抽象为接口，允许：
 * <ul>
 *   <li>运行时替换实现（如 UUID、Snowflake、Nano ID 等）；</li>
 *   <li>单元测试中注入固定 ID 的 Mock/Stub；</li>
 *   <li>未来根据部署环境切换 ID 生成方案而无需修改领域层。</li>
 * </ul>
 *
 * <h3>约定</h3>
 * <ul>
 *   <li>每次调用 {@link #nextKbId()} 必须返回全局唯一的字符串标识；</li>
 *   <li>返回值格式由实现决定，领域层不应依赖具体格式；</li>
 *   <li>方法应是无状态的、线程安全的。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
public interface KnowledgeBaseIdGenerator {

    /**
     * 生成下一个知识库业务键。
     *
     * @return 全局唯一的知识库标识符（不可为 {@code null} 或空字符串）
     */
    String nextKbId();
}
