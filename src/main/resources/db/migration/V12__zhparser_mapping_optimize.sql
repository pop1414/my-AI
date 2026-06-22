-- V12__zhparser_mapping_optimize.sql
-- 优化：洗牌 chinese 分词词性映射。
--       1. 恢复 v (动词：配置、搭建、优化) 和 m (数词：16、1024、001)。
--       2. 移除 d (副词：到底、最、非常) 和 r (代词：什么、怎么)，从源头彻底过滤口语无意义功能词。
--
-- 修改效果对比：
--   V11 配置：n, a, d, i, j, l, x, e （遗漏了技术动词 v 和技术数字 m，保留了副词干扰词 d）
--   V12 优化：n, v, a, i, j, l, x, e, m （补齐技术词 v 和 m，剔除了副词 d 和代词 r）

-- 1. 删除旧配置（CASCADE 级联机制会安全地自动删除依赖它的 content_tsv 列及索引）
DROP TEXT SEARCH CONFIGURATION IF EXISTS chinese CASCADE;

-- 2. 重新创建精准的中英混排核心词性配置
CREATE TEXT SEARCH CONFIGURATION chinese (PARSER = zhparser);
ALTER TEXT SEARCH CONFIGURATION chinese ADD MAPPING FOR n,v,a,i,j,l,x,e,m WITH simple;

-- 3. 重新创建 GIN 计算列（因为第 1 步 CASCADE 已将其干净删除）
ALTER TABLE vector_store
    ADD COLUMN content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('chinese', content)) STORED;

-- 4. 重新创建 GIN 索引
CREATE INDEX idx_vector_store_fts
    ON vector_store USING GIN (content_tsv);