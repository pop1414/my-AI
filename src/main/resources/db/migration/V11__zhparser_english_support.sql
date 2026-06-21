-- V11__zhparser_english_support.sql
-- 修复：zhparser 功能词（动词、代词）被当作有效 token，导致 plainto_tsquery 的 AND 语义
--       将"是什么"、"怎么"等疑问词变成强制匹配条件，中文问题几乎无法命中。
--
-- 问题示例：
--   plainto_tsquery('chinese', 'PGVector到底是什么技术')
--   → 'pgvector' & '到底' & '是' & '什么' & '技术'   ← 全 AND，5 个词必须同时存在
--   → 技术文档不含"到底"、"什么"等口语词 → 0 结果
--
-- 解决：重建 chinese 配置，只保留有实际语义的词性：
--   n(名词) a(形容词) d(副词) i(成语) j(简称) l(习惯用语) x(非语素字) e(英文)
--   去掉 v(动词) 和 r(代词) — "是/到底/什么/怎么"等功能词不再进入索引和查询

-- 1. 删除旧配置（CASCADE 同时移除依赖它的 content_tsv 列）
DROP TEXT SEARCH CONFIGURATION IF EXISTS chinese CASCADE;

-- 2. 重建精简的词性映射（去掉 v 和 r）
CREATE TEXT SEARCH CONFIGURATION chinese (PARSER = zhparser);
ALTER TEXT SEARCH CONFIGURATION chinese ADD MAPPING FOR n,a,d,i,j,l,x,e WITH simple;

-- 3. 重建 tsvector 列和 GIN 索引
ALTER TABLE vector_store
    ADD COLUMN content_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('chinese', content)) STORED;

CREATE INDEX idx_vector_store_fts
    ON vector_store USING GIN (content_tsv);
