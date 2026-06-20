package io.github.spike.myai.qa.infrastructure.classifier;

import io.github.spike.myai.qa.domain.model.QueryType;
import io.github.spike.myai.qa.domain.port.QueryClassifierPort;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 基于优先级规则的查询分类器。
 *
 * <p>通过正则匹配用户查询中的关键词，按优先级顺序返回首个命中的查询类型。
 * 规则优先级：CHITCHAT &gt; PROCEDURAL &gt; FACTOID &gt; COMPARATIVE &gt; GENERAL。
 * 纯 Java String/Regex 实现，逻辑层零外部依赖。
 * 通过 {@code @Component} 注册为 Spring Bean，由应用层通过端口接口注入使用。
 *
 * @author spike
 * @since 1.0.0
 */
@Component
public class RuleBasedQueryClassifier implements QueryClassifierPort {

    // === 优先级 1（最高）：CHITCHAT ===
    // 问候/感谢/闲聊关键词，必须在所有其他规则之前匹配
    private static final Pattern CHITCHAT_PATTERN = Pattern.compile(
            "你好|您好|嗨|哈喽|hello|hi|hey|谢谢|感谢|thanks|天气|开心|无聊|哈哈|呵呵|"
            + "再见|拜拜|辛苦了|辛苦|早安|晚安|早上好|晚上好",
            Pattern.CASE_INSENSITIVE);

    // === 优先级 2：PROCEDURAL ===
    // 疑问词 + 操作动词组合：如何/怎么/怎样 + 动作词，或独立的步骤/教程类关键词
    // 含英文操作词（how to, step, tutorial, guide）支持中英混合查询
    private static final Pattern PROCEDURAL_PATTERN = Pattern.compile(
            "如何|怎么|怎样|步骤|教程|指南|攻略|how to|how do|how can|step|tutorial|guide",
            Pattern.CASE_INSENSITIVE);

    // === 优先级 3：FACTOID ===
    // 疑问词 + 定义/概念：什么是/是什么/为什么 + 概念性关键词
    private static final Pattern FACTOID_PATTERN = Pattern.compile(
            "什么是|什么叫|是什么|定义|含义|介绍|解释|说明|概念|意思|"
            + "有哪些|哪些|几个|多少|为什么|原理|作用|功能|特点|优缺点",
            Pattern.CASE_INSENSITIVE);

    // === 优先级 4：COMPARATIVE ===
    // 比较关键词
    private static final Pattern COMPARATIVE_PATTERN = Pattern.compile(
            "对比|比较|区别|差异|不同|vs|versus|哪个好|哪个更好|优劣|选择|推荐",
            Pattern.CASE_INSENSITIVE);

    // === 优先级 5（默认）：GENERAL ===
    // 以上均不匹配时返回 GENERAL，无对应 pattern

    /**
     * {@inheritDoc}
     *
     * <p>按优先级依次匹配规则，首个命中即返回。
     * 空字符串或 null 返回 {@link QueryType#GENERAL}。
     */
    @Override
    public QueryType classify(String question) {
        if (question == null || question.isBlank()) {
            return QueryType.GENERAL;
        }
        if (CHITCHAT_PATTERN.matcher(question).find()) {
            return QueryType.CHITCHAT;
        }
        if (PROCEDURAL_PATTERN.matcher(question).find()) {
            return QueryType.PROCEDURAL;
        }
        if (FACTOID_PATTERN.matcher(question).find()) {
            return QueryType.FACTOID;
        }
        if (COMPARATIVE_PATTERN.matcher(question).find()) {
            return QueryType.COMPARATIVE;
        }
        return QueryType.GENERAL;
    }
}
