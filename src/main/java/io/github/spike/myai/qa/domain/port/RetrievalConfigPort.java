package io.github.spike.myai.qa.domain.port;

/**
 * 检索参数配置端口（Domain Port）。
 *
 * <p>定义问答检索阶段所需的候选集放大策略参数。
 * 应用层通过此端口读取配置，而不直接依赖基础设施层的配置属性类，
 * 保持六边形架构 application → domain 的单向依赖。
 *
 * @author spike
 * @since 1.0.0
 */
public interface RetrievalConfigPort {

    /**
     * 检索候选下限。
     *
     * <p>当用户请求的 topK 较小时，确保至少召回该数量的候选，
     * 避免权限过滤后候选过少导致无结果。
     *
     * @return 候选下限数量
     */
    int getMinCandidates();

    /**
     * 检索候选放大倍率。
     *
     * <p>先粗召回 topK × candidateMultiplier 条候选，
     * 再按权限和版本范围精过滤，提升召回率。
     *
     * @return 候选放大倍率
     */
    int getCandidateMultiplier();
}
