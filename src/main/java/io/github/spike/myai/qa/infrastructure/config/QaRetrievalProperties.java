package io.github.spike.myai.qa.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.github.spike.myai.qa.domain.port.RetrievalConfigPort;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * QA 检索参数配置属性。
 *
 * <p>绑定 {@code myai.qa.retrieval} 前缀的 YAML 配置，控制检索候选集的放大策略：
 * <ul>
 *   <li>{@code minCandidates}：检索候选下限，避免 topK 较小时过滤后无结果</li>
 *   <li>{@code candidateMultiplier}：候选放大倍率，先粗召回再按权限精过滤</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "myai.qa.retrieval")
public class QaRetrievalProperties implements RetrievalConfigPort {

    /** 检索候选下限，避免 topK 较小时候选过少导致过滤后无结果 */
    @Min(1)
    @Max(1000)
    private int minCandidates = 20;

    /** 检索候选放大倍率：先粗召回 topK×N 条，再按 kbId 精过滤 */
    @Min(1)
    @Max(100)
    private int candidateMultiplier = 4;

    /** RRF 平滑常数（k），标准值 60；小数据集可适当降低以放大头部优势 */
    @Min(1)
    @Max(500)
    private int rrfK = 60;

    /** Dense（向量检索）路径权重，值越大 Dense 对融合排名影响越大 */
    @Min(0)
    @Max(1)
    private double denseWeight = 0.7;

    /** Sparse（全文检索）路径权重，值越大 Sparse 对融合排名影响越大 */
    @Min(0)
    @Max(1)
    private double sparseWeight = 0.3;
}
