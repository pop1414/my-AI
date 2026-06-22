-- V13__zhparser_full_mapping_with_query_preprocessing.sql
-- 策略：恢复最完整词性映射（n, v, a, d, i, j, l, x, e, m），
--       仅排除 r (代词：什么、怎么、哪个)，查询端噪声由 Java preprocessQuery() 处理。
--
-- 背景：
--   V11 (n,a,d,i,j,l,x,e)  — 去 v 去 r，粗暴但意外地好（50% HitRate）
--   V12 (n,v,a,i,j,l,x,e,m) — 加 v 去 d 去 r，助动词"是""该"杀死 AND 查询（23% HitRate）
--   V13 (n,v,a,d,i,j,l,x,e,m) — 加回 v 和 d，Java 层查询预处理清除口语停用词
--
-- V12 暴跌根因：
--   1. v 恢复后，"是"(v)、"该"(v) 进入 plainto_tsquery 的 AND 条件，文档中无此词 → 零结果
--   2. d 移除后，"最"(d)、"近似"(d) 等技术副词丢失
--
-- V13 解法：
--   数据库端保留全部有价值词性（"配置"v、"优化"v、"近似"d、"最"d）
--   Java 端 preprocessQuery() 在送入 plainto_tsquery 前清除口语停用词（"是""该""什么"）

-- 1. 删除旧配置（CASCADE 级联删除依赖列和索引）
DROP TEXT SEARCH CONFIGURATION IF EXISTS chinese CASCADE;

-- 2. 创建最完整映射：保留 v(动词) 和 d(副词)，仅排除 r(代词)
CREATE TEXT SEARCH CONFIGURATION chinese (PARSER = zhparser);
ALTER TEXT SEARCH CONFIGURATION chinese ADD MAPPING FOR n,v,a,d,i,j,l,x,e,m WITH simple;

-- 3. 重建 tsvector 计算列
ALTER TABLE vector_store
    ADD COLUMN content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('chinese', content)) STORED;

-- 4. 重建 GIN 索引
CREATE INDEX idx_vector_store_fts
    ON vector_store USING GIN (content_tsv);
