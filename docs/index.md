# my-AI 项目文档索引

> **AI Agent 入口文件** — 本文件是 BMad 工作流的 `project_knowledge` 主索引。

## 项目概览

| 属性 | 值 |
|---|---|
| 项目名称 | my-AI |
| 类型 | AI 知识管理平台（RAG） |
| 架构 | DDD-Lite（六边形架构简化版） |
| 主要语言 | Java 21 |
| 框架 | Spring Boot 3.5.8 + Spring AI 1.1.2 |
| 数据库 | PostgreSQL 16 + PGVector |
| 文档解析 | Docling Serve（arconia-docling-spring-boot-starter） |
| 子域 | auth / ingest / knowledge / qa |

## 文档目录结构

```
docs/
├── index.md                    ← 你在这里（主索引）
├── architecture/
│   ├── overview.md             # 架构设计总览
│   └── domain/                 # 子域架构（按需增长）
├── api/
│   └── contracts.md            # REST API 契约
├── data/
│   └── models.md               # 数据库模型
├── guides/
│   ├── project-overview.md     # 项目概述 + 快速开始
│   ├── development.md          # 开发指南
│   └── source-tree.md          # 源码结构
├── adr/                        # 架构决策记录（7 篇 ADR）
│   └── index.md                # ADR 索引 + 决策演化图
├── specs/                      # 详细规格文档（按子域组织）
│   ├── index.md                # specs 索引（待生成）
│   ├── auth/                   # auth 子域规格（按需增长）
│   ├── ingest/                 # ingest 子域规格（按需增长）
│   ├── knowledge/              # knowledge 子域规格（按需增长）
│   └── qa/                     # qa 子域规格（按需增长）
└── backlog/
    ├── BACKLOG.md              # 当前活跃任务（≤15项）
    └── archive/                # 已完成/已放弃的季度归档
```

## 文档导航

### 核心文档

| 文档 | 说明 |
|---|---|
| [项目概述](./guides/project-overview.md) | 技术栈、子域总览、快速开始 |
| [架构设计](./architecture/overview.md) | DDD-Lite 分层、4 子域详解、技术选型 |
| [API 契约](./api/contracts.md) | 37 个 REST API 端点清单（源码级提取） |
| [数据模型](./data/models.md) | 14 张表结构 + Flyway V1-V8 迁移历史 |
| [源码结构](./guides/source-tree.md) | 目录树 + 关键文件注释 |
| [开发指南](./guides/development.md) | 环境变量、启动、构建、测试、代码规范 |
| [BACKLOG](./backlog/BACKLOG.md) | 当前活跃任务，按子域分组 |
| [ADR 索引](./adr/index.md) | 架构决策记录索引 + 决策演化图 |
| [文档版本读取规格](./specs/ingest/document-version-read-boundary-spec.md) | ADR-0006 详细约束规则 |

### 现有项目文档

| 文档 | 说明 |
|---|---|
| [README.md](../README.md) | 项目说明（含完整 API 摘要、V1 闭环演示） |
| [DESIGN.md](../DESIGN.md) | UI 设计系统规范（颜色/间距/圆角/字体 Token） |
| [project-context.md](./project-context.md) | AI Agent 编码规则（162 条） |

## 快速参考

### 子域 → API 路径映射

| 子域 | API 前缀 | 端点数 |
|---|---|---|
| auth | `/api/v1/auth`, `/api/v1/admin/*` | 22 |
| ingest | `/api/v1/documents` | 10 |
| knowledge | `/api/v1/knowledge-bases` | 4 |
| qa | `/api/v1/qa` | 1 |

### 关键入口文件

| 用途 | 文件 |
|---|---|
| 应用入口 | `src/main/java/io/github/spike/myai/MyAiApplication.java` |
| 安全配置 | `src/main/java/io/github/spike/myai/auth/security/SecurityConfig.java` |
| Ingest 配置 | `src/main/java/io/github/spike/myai/ingest/infrastructure/config/IngestProperties.java` |
| RAG 管线 | `src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java` |
| 文档处理 | `src/main/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationService.java` |
| 全局异常处理 | `src/main/java/io/github/spike/myai/shared/rest/GlobalRestExceptionHandler.java` |
| 数据库迁移 | `src/main/resources/db/migration/` |

---

_最后更新: 2026-06-19 | 扫描模式: 深度扫描 | 变更: Tika→Docling 迁移完成_
