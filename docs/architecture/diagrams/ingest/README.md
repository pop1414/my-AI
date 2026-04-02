# Ingest 领域图纸索引

本目录存放 `ingest` 领域的设计图，采用固定的 5 张标准图套餐。

## A. 受理闭环（Acceptance Closure）
### 1. 用例图（功能边界）
- `ingest-acceptance-closure-usecase.puml`
- `ingest-acceptance-closure-usecase.png`

### 2. 组件图（分层与职责）
- `ingest-acceptance-closure-components.puml`
- `ingest-acceptance-closure-components.png`

### 3. 时序图（核心流程交互）
- `ingest-acceptance-closure-sequence.puml`
- `ingest-acceptance-closure-sequence.png`

### 4. 状态机图（状态流转规则）
- `ingest-acceptance-closure-state.puml`
- `ingest-acceptance-closure-state.png`

### 5. ER/领域模型图（数据结构对齐）
- `ingest-acceptance-closure-er-domain.puml`
- `ingest-acceptance-closure-er-domain.png`

## B. 处理执行（Processing Execution）
### 1. 用例图（功能边界）
- `ingest-processing-execution-usecase.puml`

### 2. 组件图（分层与职责）
- `ingest-processing-execution-components.puml`

### 3. 时序图（核心流程交互）
- `ingest-processing-execution-sequence.puml`

### 4. 状态机图（状态流转规则）
- `ingest-processing-execution-state.puml`

### 5. ER/领域模型图（数据结构对齐）
- `ingest-processing-execution-er-domain.puml`

## 命名约定
- `puml` 是图纸源码，优先维护；
- `png` 是渲染产物，用于快速预览与文档展示；
- 命名统一：`<domain>-<topic>-<view>.<ext>`。
