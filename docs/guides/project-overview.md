# my-AI 项目概述

## 项目简介

my-AI 是一个基于 Spring Boot + Spring AI 的 **AI 知识管理平台**，核心能力是"上传 → 可追踪 → 可索引 → 可问答"的最小闭环。用户上传文档后，系统自动解析、分块、向量化，最终通过 RAG（检索增强生成）实现基于私有文档的智能问答。

## 技术栈

| 组件 | 版本 | 用途 |
|---|---|---|
| Java | 21 (LTS) | 运行时 |
| Spring Boot | 3.5.8 | Web 框架 |
| Spring AI | 1.1.2 | AI 集成核心 |
| Spring AI Alibaba | 1.1.2.x | DashScope 适配 + Agent Framework |
| PostgreSQL + PGVector | pg16 | 关系数据 + 向量存储 |
| Flyway | — | 数据库迁移 |
| Apache Tika | 2.9.2 | 文档解析（PDF/Word/HTML 等） |
| AWS S3 SDK v2 | 2.42.14 | 对象存储（兼容 MinIO/RustFS） |
| jsoup | 1.18.3 | HTML 清洗 |
| flexmark | 0.64.8 | HTML 转 Markdown |
| Maven Wrapper | — | 构建工具 |

> **依赖说明**：项目同时依赖 `spring-ai-alibaba-extensions 1.1.2.1`，提供 DashScope Agent Framework 等扩展能力。

## 架构类型

**DDD-Lite（六边形架构简化版）**，4 个子域按 `interfaces / application / domain / infrastructure` 四层组织。

## 子域总览

| 子域 | 职责 | 关键能力 |
|---|---|---|
| **auth** | 认证、授权、治理、审计 | 三级授权模型、登录锁定、审计追踪 |
| **ingest** | 文档入库生命周期 | 上传受理、异步处理、版本管理、分块向量化 |
| **knowledge** | 知识库主数据管理 | CRUD、文档计数、软删除 |
| **qa** | 检索与回答生成 | RAG 管线（语义检索 + LLM 生成） |

## 核心数据流

```
用户上传文件
    → [ingest] 受理 + 异步处理（解析 → 分块 → 向量化）
    → [knowledge] 知识库聚合已索引文档
    → [qa] 语义检索 + LLM 回答生成
    → 用户获得答案 + 引用分块
```

## 快速开始

### 前置条件

- JDK 21
- Docker（用于 PostgreSQL + RustFS）
- DashScope API Key

### 启动步骤

```bash
# 1. 启动基础设施（PostgreSQL + RustFS）
cd infra && docker compose up -d

# 2. 启动后端
.\mvnw.cmd spring-boot:run   # Windows
# ./mvnw spring-boot:run     # Linux/macOS

# 3. 启动前端（可选）
cd web && npm install && npm run dev
```

### 关键环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DASHSCOPE_API_KEY` | — | DashScope API 密钥（必填） |
| `DASHSCOPE_CHAT_MODEL` | `qwen-plus` | 聊天模型 |
| `DASHSCOPE_EMBEDDING_MODEL` | `text-embedding-v4` | Embedding 模型 |
| `PGVECTOR_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/myai` | 数据库连接 |
| `MYAI_AUTH_BOOTSTRAP_ADMIN_USERNAME` | — | 首个管理员用户名（空库引导） |
| `MYAI_AUTH_BOOTSTRAP_ADMIN_PASSWORD` | — | 首个管理员密码 |

### 运行测试

```bash
# 纯单测（不依赖数据库，快）
.\mvnw.cmd "-Dtest=!MyAiApplicationTests" test

# 完整测试（含集成测试，需 PostgreSQL）
.\mvnw.cmd test
```

## 文档导航

| 文档 | 说明 |
|---|---|
| [架构设计](../architecture/overview.md) | 分层架构、子域结构、技术选型 |
| [API 契约](../api/contracts.md) | 全量 REST API 端点清单 |
| [数据模型](../data/models.md) | 数据库表结构、关系、迁移历史 |
| [源码结构](./source-tree.md) | 目录树 + 关键文件说明 |
| [开发指南](./development.md) | 本地开发、构建、测试、部署 |

---

_生成时间: 2026-06-15 | 扫描模式: 深度扫描_
