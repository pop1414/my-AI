package io.github.spike.myai.shared.workspace;

/**
 * 工作区（Workspace）相关常量。
 *
 * <p>当前系统运行于<b>单工作区模式</b>，但数据库表结构与运行时代码
 * 均显式携带 {@code workspace_id} 字段，以避免继续依赖隐式单租户假设。
 *
 * <h3>设计意图</h3>
 * <ul>
 *   <li><b>前瞻性</b>：为未来多工作区/多租户架构预留扩展空间，
 *       避免届时大规模数据库迁移；</li>
 *   <li><b>一致性</b>：所有领域聚合根与读模型均携带 {@code workspaceId}，
 *       通过便利构造器自动填充默认值，调用方无需感知；</li>
 *   <li><b>数据隔离</b>：即使当前仅有一个工作区，SQL 查询中也强制携带
 *       {@code workspace_id} 过滤条件，为多租户切换做好准备。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
public final class WorkspaceConstants {

    /**
     * 当前阶段的默认工作区标识。
     *
     * <p>所有未显式指定工作区的领域对象均归入此工作区。
     * 未来多工作区支持时，该常量将被移除或改为动态获取。
     */
    public static final String DEFAULT_WORKSPACE_ID = "default";

    /**
     * 私有构造器，防止实例化常量类。
     *
     * <p>符合阿里巴巴 Java 开发手册中"常量类只包含静态常量，
     * 不应被实例化"的规范。
     */
    private WorkspaceConstants() {
    }
}
