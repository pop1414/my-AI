package io.github.spike.myai.qa.infrastructure.classifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.spike.myai.qa.domain.model.QueryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RuleBasedQueryClassifier 单元测试。
 *
 * <p>覆盖 5 种 QueryType 的基本分类、优先级冲突解决和边界输入场景。
 */
class RuleBasedQueryClassifierTest {

    private final RuleBasedQueryClassifier classifier = new RuleBasedQueryClassifier();

    // ========== CHITCHAT 基本分类 ==========

    @Test
    @DisplayName("classify 应将问候语'你好'识别为闲聊")
    void classify_shouldReturnChitchat_whenGreetingChinese() {
        assertEquals(QueryType.CHITCHAT, classifier.classify("你好"));
    }

    @Test
    @DisplayName("classify 应将英文问候'Hello'识别为闲聊")
    void classify_shouldReturnChitchat_whenGreetingEnglish() {
        assertEquals(QueryType.CHITCHAT, classifier.classify("Hello"));
    }

    @Test
    @DisplayName("classify 应将感谢语'谢谢你的帮助'识别为闲聊")
    void classify_shouldReturnChitchat_whenThanking() {
        assertEquals(QueryType.CHITCHAT, classifier.classify("谢谢你的帮助"));
    }

    @Test
    @DisplayName("classify 应将天气闲聊'今天天气怎么样'识别为闲聊")
    void classify_shouldReturnChitchat_whenWeatherChat() {
        assertEquals(QueryType.CHITCHAT, classifier.classify("今天天气怎么样"));
    }

    // ========== PROCEDURAL 基本分类 ==========

    @Test
    @DisplayName("classify 应将'如何配置 Flyway'识别为操作查询")
    void classify_shouldReturnProcedural_whenHowToConfigure() {
        assertEquals(QueryType.PROCEDURAL, classifier.classify("如何配置 Flyway"));
    }

    @Test
    @DisplayName("classify 应将'怎么实现向量检索'识别为操作查询")
    void classify_shouldReturnProcedural_whenHowToImplement() {
        assertEquals(QueryType.PROCEDURAL, classifier.classify("怎么实现向量检索"));
    }

    @Test
    @DisplayName("classify 应将'Spring Boot 部署步骤'识别为操作查询")
    void classify_shouldReturnProcedural_whenDeploymentSteps() {
        assertEquals(QueryType.PROCEDURAL, classifier.classify("Spring Boot 部署步骤"));
    }

    @Test
    @DisplayName("classify 应将'PostgreSQL 教程'识别为操作查询")
    void classify_shouldReturnProcedural_whenTutorial() {
        assertEquals(QueryType.PROCEDURAL, classifier.classify("PostgreSQL 教程"));
    }

    // ========== FACTOID 基本分类 ==========

    @Test
    @DisplayName("classify 应将'什么是向量数据库'识别为事实查询")
    void classify_shouldReturnFactoid_whenWhatIs() {
        assertEquals(QueryType.FACTOID, classifier.classify("什么是向量数据库"));
    }

    @Test
    @DisplayName("classify 应将'PGVector 是什么'识别为事实查询")
    void classify_shouldReturnFactoid_whenIsWhat() {
        assertEquals(QueryType.FACTOID, classifier.classify("PGVector 是什么"));
    }

    @Test
    @DisplayName("classify 应将'RAG 的原理'识别为事实查询")
    void classify_shouldReturnFactoid_whenPrinciple() {
        assertEquals(QueryType.FACTOID, classifier.classify("RAG 的原理"));
    }

    @Test
    @DisplayName("classify 应将'Spring AI 的功能有哪些'识别为事实查询")
    void classify_shouldReturnFactoid_whenFeatures() {
        assertEquals(QueryType.FACTOID, classifier.classify("Spring AI 的功能有哪些"));
    }

    // ========== COMPARATIVE 基本分类 ==========

    @Test
    @DisplayName("classify 应将'Spring AI 和 LangChain 区别'识别为对比查询")
    void classify_shouldReturnComparative_whenDifference() {
        assertEquals(QueryType.COMPARATIVE, classifier.classify("Spring AI 和 LangChain 区别"));
    }

    @Test
    @DisplayName("classify 应将'对比 Redis 和 Memcached'识别为对比查询")
    void classify_shouldReturnComparative_whenCompare() {
        assertEquals(QueryType.COMPARATIVE, classifier.classify("对比 Redis 和 Memcached"));
    }

    @Test
    @DisplayName("classify 应将'PostgreSQL vs MySQL 哪个好'识别为对比查询")
    void classify_shouldReturnComparative_whenVs() {
        assertEquals(QueryType.COMPARATIVE, classifier.classify("PostgreSQL vs MySQL 哪个好"));
    }

    @Test
    @DisplayName("classify 应将'Java 和 Python 选择哪个'识别为对比查询")
    void classify_shouldReturnComparative_whenChoosing() {
        assertEquals(QueryType.COMPARATIVE, classifier.classify("Java 和 Python 选择哪个"));
    }

    // ========== GENERAL 基本分类 ==========

    @Test
    @DisplayName("classify 应将'文档管理'识别为通用查询")
    void classify_shouldReturnGeneral_whenNounPhrase() {
        assertEquals(QueryType.GENERAL, classifier.classify("文档管理"));
    }

    @Test
    @DisplayName("classify 应将'数据存储'识别为通用查询")
    void classify_shouldReturnGeneral_whenDataStorage() {
        assertEquals(QueryType.GENERAL, classifier.classify("数据存储"));
    }

    @Test
    @DisplayName("classify 应将'系统架构'识别为通用查询")
    void classify_shouldReturnGeneral_whenSystemArchitecture() {
        assertEquals(QueryType.GENERAL, classifier.classify("系统架构"));
    }

    // ========== 优先级测试：CHITCHAT > 其他 ==========

    @Test
    @DisplayName("classify 应将同时含问候和疑问词的查询识别为闲聊（CHITCHAT 优先于 PROCEDURAL）")
    void classify_shouldReturnChitchat_whenGreetingWithQuestionWord() {
        assertEquals(QueryType.CHITCHAT, classifier.classify("你好，请问如何配置数据库"));
    }

    @Test
    @DisplayName("classify 应将同时含感谢和定义词的查询识别为闲聊（CHITCHAT 优先于 FACTOID）")
    void classify_shouldReturnChitchat_whenThankingWithDefinitionWord() {
        assertEquals(QueryType.CHITCHAT, classifier.classify("谢谢，什么是向量数据库"));
    }

    @Test
    @DisplayName("classify 应将含问候的比较查询识别为闲聊（CHITCHAT 优先于 COMPARATIVE）")
    void classify_shouldReturnChitchat_whenGreetingWithComparison() {
        assertEquals(QueryType.CHITCHAT, classifier.classify("嗨，Spring AI 和 LangChain 哪个好"));
    }

    // ========== 优先级测试：PROCEDURAL > FACTOID ==========

    @Test
    @DisplayName("classify 应将'如何使用向量数据库的原理'识别为操作查询（PROCEDURAL 优先于 FACTOID）")
    void classify_shouldReturnProcedural_whenHowToWithFactoidWords() {
        assertEquals(QueryType.PROCEDURAL, classifier.classify("如何使用向量数据库的原理"));
    }

    // ========== 优先级测试：PROCEDURAL > COMPARATIVE ==========

    @Test
    @DisplayName("classify 应将'如何对比 Spring AI 和 LangChain'识别为操作查询（PROCEDURAL 优先于 COMPARATIVE）")
    void classify_shouldReturnProcedural_whenHowToCompare() {
        assertEquals(QueryType.PROCEDURAL, classifier.classify("如何对比 Spring AI 和 LangChain"));
    }

    // ========== 优先级测试：COMPARATIVE > FACTOID ==========

    @Test
    @DisplayName("classify 应将含比较多词且无 PROCEDURAL 关键词的查询识别为对比查询（COMPARATIVE 优先于 FACTOID）")
    void classify_shouldReturnComparative_whenComparisonWithFactoidWords() {
        assertEquals(QueryType.COMPARATIVE, classifier.classify("Redis 和 Memcached 有什么不同"));
    }

    // ========== 边界测试 ==========

    @Test
    @DisplayName("classify 应将 null 输入识别为通用查询")
    void classify_shouldReturnGeneral_whenNull() {
        assertEquals(QueryType.GENERAL, classifier.classify(null));
    }

    @Test
    @DisplayName("classify 应将空字符串识别为通用查询")
    void classify_shouldReturnGeneral_whenEmpty() {
        assertEquals(QueryType.GENERAL, classifier.classify(""));
    }

    @Test
    @DisplayName("classify 应将纯空白字符串识别为通用查询")
    void classify_shouldReturnGeneral_whenWhitespace() {
        assertEquals(QueryType.GENERAL, classifier.classify("   "));
    }

    @Test
    @DisplayName("classify 应将纯标点符号识别为通用查询")
    void classify_shouldReturnGeneral_whenOnlyPunctuation() {
        assertEquals(QueryType.GENERAL, classifier.classify("?!@#$%^&*"));
    }

    @Test
    @DisplayName("classify 应正确分类含关键词的超长文本")
    void classify_shouldReturnCorrectType_whenLongText() {
        String longText = "这是一段很长的文本，".repeat(50) + "如何配置 Spring Boot";
        assertEquals(QueryType.PROCEDURAL, classifier.classify(longText));
    }

    @Test
    @DisplayName("classify 应对英文查询进行大小写不敏感匹配")
    void classify_shouldMatchCaseInsensitive_whenEnglishQuery() {
        assertEquals(QueryType.CHITCHAT, classifier.classify("HELLO"));
        assertEquals(QueryType.CHITCHAT, classifier.classify("hello"));
        assertEquals(QueryType.PROCEDURAL, classifier.classify("How to configure Flyway"));
    }
}
