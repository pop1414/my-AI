---
stepsCompleted: ['step-01-preflight', 'step-02-generate-pipeline', 'step-03-configure-quality-gates', 'step-04-validate-and-summary']
lastStep: 'step-04-validate-and-summary'
lastSaved: '2026-06-15'
---

# CI 流水线搭建进度

## 步骤 1：预检

- **测试技术栈类型**：全栈（Java 21 后端 + React 19 前端）
- **测试框架**：JUnit 5 + Surefire（后端）、Playwright（前端）
- **CI 平台**：`github-actions`（默认选择，未检测到已有 CI 配置）
- **Java 版本**：21（来自 pom.xml `<java.version>21</java.version>`）
- **Node 版本**：22（最新 LTS，来自 web/package.json engines 或默认值）
- **Maven 缓存**：通过 `actions/setup-java` 的 `cache: maven` 启用
- **npm 缓存**：通过 `actions/setup-node` 的 `cache: npm` 启用
- **已有 CI 配置**：未检测到
- **Git 远程仓库**：已配置

## 步骤 2：生成流水线

- **输出路径**：`.github/workflows/ci.yml`
- **流水线类型**：两阶段（快速反馈 + 集成测试）
- **阶段 1（并行执行）**：
  - `backend-build`：`./mvnw clean compile` + `./mvnw test "-Dtest=!MyAiApplicationTests"`
  - `frontend-build`：`npm ci` + `npm run build`（tsc + vite）+ `npm run lint`
- **阶段 2（条件执行，仅 main 分支或手动触发）**：
  - `integration-test`：完整 `./mvnw test`，使用 PostgreSQL 服务容器
  - `e2e-test`：Playwright E2E 测试，配合后端 + PostgreSQL
- **制品上传**：Surefire 测试报告、Playwright 报告
- **并发控制**：每个 ref 开启 `cancel-in-progress: true`

## 步骤 3：质量门禁

- **稳定性测试**：已跳过（以后端为主的项目，后端测试具有确定性）
- **质量门禁**：所有阶段 1 的任务必须通过才能合并 PR
- **通知**：未配置（无可用的 Slack/邮箱 secrets）
- **契约测试**：不适用（项目中无 Pact/契约测试）

## 步骤 4：验证

- [x] CI 配置文件已创建于 `.github/workflows/ci.yml`
- [x] 阶段 1 并行任务已配置（后端 + 前端）
- [x] 阶段 2 条件任务已配置（集成测试 + E2E）
- [x] 依赖缓存已启用（Maven + npm）
- [x] 失败时上传制品（测试报告、Playwright 报告）
- [x] 并发控制已配置
- [x] 每个任务已设置超时时间
- [x] PostgreSQL 服务容器已定义（集成测试 + E2E）

### 需要配置的 Secrets

| Secret 名称 | 使用方 | 说明 |
|---|---|---|
| `DASHSCOPE_API_KEY` | integration-test、e2e-test | DashScope API 密钥，用于 AI 功能 |

### 后续步骤

1. 推送分支并验证阶段 1 任务是否通过
2. 如需运行集成测试，在 GitHub 仓库 Settings → Secrets 中配置 `DASHSCOPE_API_KEY`
3. 合入 main 后验证阶段 2 任务是否正常触发
