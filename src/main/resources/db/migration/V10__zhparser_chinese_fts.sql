-- V10__zhparser_chinese_fts.sql
-- 引入 zhparser 中文分词扩展，替换 'simple' 配置解决中文全文检索命中率问题
--
-- 背景：V9 使用 to_tsvector('simple', content) 对中文按行/整段作为单个 token，
-- 导致 plainto_tsquery 对中文查询几乎无法命中。zhparser 基于中文分词算法，
-- 能将 "向量检索技术" 拆分为 "向量"、"检索"、"技术" 等有意义的词单元。

-- 1. 安装 zhparser 扩展
CREATE EXTENSION IF NOT EXISTS zhparser;

-- 2. 创建基于 zhparser 的中文全文检索配置
CREATE TEXT SEARCH CONFIGURATION chinese (PARSER = zhparser);

-- 3. 映射 zhparser 词性到 simple 字典（保留所有有意义的词性）
--    zhparser 词性参考：n=名词 v=动词 a=形容词 d=副词 i=成语 j=简称 l=习惯用语
--    x=非语素字（保留，用于处理未识别词和英文混排）
ALTER TEXT SEARCH CONFIGURATION chinese ADD MAPPING FOR n,v,a,d,i,j,l,x WITH simple;

-- 4. 删除旧的 'simple' tsvector 列和索引
DROP INDEX IF EXISTS idx_vector_store_fts;
ALTER TABLE vector_store DROP COLUMN IF EXISTS content_tsv;

-- 5. 使用 zhparser 配置重建 tsvector 生成列和 GIN 索引
ALTER TABLE vector_store
    ADD COLUMN content_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('chinese', content)) STORED;

CREATE INDEX idx_vector_store_fts
    ON vector_store USING GIN (content_tsv);
