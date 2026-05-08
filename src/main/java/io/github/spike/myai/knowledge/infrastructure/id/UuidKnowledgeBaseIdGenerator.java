package io.github.spike.myai.knowledge.infrastructure.id;

import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 基于 UUID v4 的知识库业务键生成器（基础设施层适配器）。
 *
 * <p>该类是 {@link KnowledgeBaseIdGenerator} 端口接口的 UUID 实现，
 * 位于六边形架构的<b>基础设施层</b>。
 *
 * <h3>方案选型理由</h3>
 * <ul>
 *   <li><b>全局唯一</b>：{@link UUID#randomUUID()} 使用加密强度的伪随机数
 *       生成 UUID v4，碰撞概率极低（约 2⁻¹²²），无需中心化协调；</li>
 *   <li><b>实现简单</b>：JDK 内置 {@code java.util.UUID}，无外部依赖；</li>
 *   <li><b>无状态</b>：每次调用独立生成，天然线程安全，适合无状态服务水平扩展。</li>
 * </ul>
 *
 * <h3>输出格式</h3>
 * <p>{@link UUID#randomUUID()} 返回格式为
 * {@code 550e8400-e29b-41d4-a716-446655440000}（36 字符，含 4 个连字符）。
 *
 * <h3>局限性</h3>
 * <ul>
 *   <li>UUID v4 无序性可能导致数据库 B+ 树索引页分裂，高并发写入场景可考虑
 *       UUID v7 或 Snowflake 等有序 ID 方案；</li>
 *   <li>36 字符长度略长，如需短标识可改用 Nano ID 或 ULID。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 * @see KnowledgeBaseIdGenerator
 */
@Component
public class UuidKnowledgeBaseIdGenerator implements KnowledgeBaseIdGenerator {

    /**
     * 生成基于 UUID v4 的知识库业务键。
     *
     * <p>每次调用返回一个新的全局唯一标识符，格式为
     * {@code 550e8400-e29b-41d4-a716-446655440000}。
     *
     * @return UUID v4 字符串（36 字符，不可为 {@code null}）
     */
    @Override
    public String nextKbId() {
        // 使用 JDK 内置的 UUID 随机生成器，基于 SecureRandom 实现
        // UUID.randomUUID() 内部调用 SecureRandom 获取加密强度随机数
        return UUID.randomUUID().toString();
    }
}
