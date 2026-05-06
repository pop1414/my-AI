# 学习沉淀导航

`docs/learning/` 用来记录“我为什么这样设计、我真正学会了什么、面试时该怎么讲”。

这类文档不替代工程真源，而是帮助你：

1. 把做过的事情转化成自己的理解
2. 积累可复用的技术认知，而不是只记住项目表面
3. 为面试中高频的深挖问题提前准备可复述材料

## 1. 和正式文档的区别

- `docs/`：描述当前系统事实，偏客观
- `docs/learning/`：描述你的理解、取舍、复盘，允许主观视角
- `deliverables/course/`：描述课程提交和展示材料，偏交付表达

换句话说：

- 正式文档回答“系统是什么”
- 学习文档回答“我为什么这么做、我学到了什么”

## 2. 当前分区

- [backend/](./backend/)：后端实现、分层、并发、状态机、异常处理
- [rag/](./rag/)：RAG 主链路、分块、检索、提示词、引用设计
- [database/](./database/)：PGVector、表结构、自检、数据一致性
- [frontend/](./frontend/)：控制台设计、前后端契约、页面交互
- [devlog/](./devlog/)：阶段性复盘、开发日记、下一步关注点

## 3. 每篇学习文档推荐模板

建议每篇都尽量覆盖下面几部分：

1. 背景 / 问题
2. 这次项目里我是怎么遇到它的
3. 我最后采用了什么方案
4. 为什么不用别的方案
5. 这件事面试官可能怎么问
6. 我该怎么回答
7. 相关代码 / 正式文档入口

## 4. 命名建议

推荐使用“时间 + 主题 + notes”的稳定格式，例如：

- `2026-05-rag-chunking-notes.md`
- `2026-05-pgvector-indexing-notes.md`
- `2026-05-worker-retry-design.md`

## 5. 当前首批沉淀

- [rag/2026-05-rag-chunking-notes.md](./rag/2026-05-rag-chunking-notes.md)
- [database/2026-05-pgvector-indexing-notes.md](./database/2026-05-pgvector-indexing-notes.md)
- [backend/2026-05-worker-retry-design.md](./backend/2026-05-worker-retry-design.md)

## 6. 使用规则

- 每做完一个重要功能，至少补一篇学习沉淀
- 学习文档可以写“踩坑”和“错误理解”，但最后要给出当前结论
- 如果学习结论已经变成系统正式事实，要同步更新 `docs/` 对应正式文档
