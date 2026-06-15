# 开发指南

## 前置条件

| 依赖 | 版本要求 | 说明 |
|---|---|---|
| JDK | 21（LTS） | 不可使用 22+ 特性 |
| Docker | 最新版 | 用于 PostgreSQL + RustFS |
| Node.js | — | 仅前端开发需要（`web/` 目录） |
| DashScope API Key | — | 阿里云百炼平台申请 |

## 环境变量

### 必填

| 变量 | 说明 |
|---|---|
| `DASHSCOPE_API_KEY` | DashScope API 密钥 |

### 选填（有默认值）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PGVECTOR_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/myai` | 数据库连接 |
| `PGVECTOR_DATASOURCE_USERNAME` | `admin` | 数据库用户 |
| `PGVECTOR_DATASOURCE_PASSWORD` | `admin` | 数据库密码 |
| `DASHSCOPE_CHAT_MODEL` | `qwen-plus` | 聊天模型 |
| `DASHSCOPE_EMBEDDING_MODEL` | `text-embedding-v4` | Embedding 模型 |
| `DASHSCOPE_EMBEDDING_DIMENSIONS` | `1024` | 向量维度 |
| `MYAI_AUTH_BOOTSTRAP_ADMIN_USERNAME` | — | 首个管理员用户名 |
| `MYAI_AUTH_BOOTSTRAP_ADMIN_PASSWORD` | — | 首个管理员密码 |
| `MYAI_AUTH_BOOTSTRAP_ADMIN_DISPLAY_NAME` | — | 首个管理员显示名 |
| `INGEST_WORKER_ENABLED` | `true` | 异步 Worker 开关 |
| `INGEST_WORKER_POLL_DELAY_MS` | `5000` | Worker 轮询间隔 |
| `INGEST_STORAGE_TYPE` | `s3` | 存储类型（`local` / `s3`） |
| `INGEST_STORAGE_S3_ENDPOINT` | `http://localhost:9000` | S3 endpoint |
| `INGEST_STORAGE_S3_BUCKET` | `myai-documents` | S3 bucket |
| `INGEST_STORAGE_S3_ACCESS_KEY` | — | S3 access key |
| `INGEST_STORAGE_S3_SECRET_KEY` | — | S3 secret key |
| `INGEST_CHUNK_SIZE` | `500` | 分块大小（字符） |
| `INGEST_CHUNK_OVERLAP` | `100` | 分块重叠（字符） |
| `INGEST_SCHEMA_CHECK_ENABLED` | `true` | 启动时 schema 自检 |
| `INGEST_PARSER_MAX_TEXT_LENGTH` | `2000000` | Tika 解析最大文本长度 |
| `INGEST_PARSER_PARSE_EMBEDDED_RESOURCE` | `false` | 是否允许解析嵌入资源 |
| `INGEST_STORAGE_ROOT_DIR` | `data/ingest` | local 模式本地落盘目录 |
| `INGEST_STORAGE_S3_REGION` | `us-east-1` | S3 region |
| `INGEST_STORAGE_S3_PATH_STYLE_ACCESS` | `true` | S3 path-style access（本地 MinIO/RustFS 用 true） |
| `INGEST_STORAGE_ARTIFACT_MAX_READ_BYTES` | `2000000` | 正文读取单次最大字节数 |
| `INGEST_STORAGE_KEEP_RAW_XHTML` | `false` | 是否保留 Tika 原始 XHTML 调试产物 |
| `INGEST_STORAGE_KEEP_CLEANED_HTML` | `false` | 是否保留 cleaned.html 调试产物 |
| `INGEST_STORAGE_KEEP_PARSE_RESULT_JSON` | `true` | 是否保留 parse-result.json |

## 本地开发启动

### 启动步骤

```bash
# 1. 启动基础设施（PostgreSQL + RustFS）
cd infra && docker compose up -d

# 2. 设置环境变量
export DASHSCOPE_API_KEY=your-api-key
export MYAI_AUTH_BOOTSTRAP_ADMIN_USERNAME=admin
export MYAI_AUTH_BOOTSTRAP_ADMIN_PASSWORD=admin123

# 3. 启动后端
.\mvnw.cmd spring-boot:run    # Windows
# ./mvnw spring-boot:run      # Linux/macOS

# 4. 启动前端（可选）
cd web && npm install && npm run dev
```

### 前端开发

```bash
cd web
npm install
npm run dev        # 访问 http://localhost:55555
npm run build      # TypeScript 编译 + Vite 生产构建
npm run lint       # ESLint 代码检查
```

Vite 开发服务器配置：端口 55555，`/api` 代理到 `localhost:8080`。

## 构建

```bash
# 必须使用 Maven Wrapper（不要用系统 mvn）
.\mvnw.cmd clean package        # Windows
# ./mvnw clean package          # Linux/macOS

# 跳过测试构建
.\mvnw.cmd clean package -DskipTests
```

## 测试

### 后端单元测试

```bash
# 纯单测（不依赖数据库，推荐日常开发使用）
.\mvnw.cmd "-Dtest=!MyAiApplicationTests" test

# 完整测试（含集成测试，需要本地 PostgreSQL）
.\mvnw.cmd test
```

**测试约定**：
- Service 层测试不用 `@SpringBootTest`，手动构造 + Mockito mock
- 测试方法 `@DisplayName` 使用中文
- 测试辅助方法放测试类底部
- `MyAiApplicationTests` 是集成测试，需要完整 Spring 上下文

### 前端 E2E 测试

```bash
cd web
npm run test:e2e           # 无头模式
npm run test:e2e:headed    # 有头模式
npm run test:e2e:debug     # 调试模式
```

## 数据库迁移

```bash
# Flyway 自动在应用启动时执行迁移
# 不要修改已应用的迁移文件（checksum 校验会拒绝启动）

# 新迁移文件命名规范
# src/main/resources/db/migration/V{version}__{description}.sql
```

## 代码规范

### Java 后端

| 规则 | 说明 |
|---|---|
| 分层 | DDD-Lite：interfaces → application → domain ← infrastructure |
| 注入 | 只用构造器注入，禁止 `@Autowired` 字段注入 |
| 日志 | `@Slf4j`（Lombok），禁止 `System.out` |
| 异常 | `BusinessException(HttpStatus, code, message)`，禁止裸 `RuntimeException` |
| 配置 | `@ConfigurationProperties` 类型安全绑定 |
| Javadoc | 中文，含 `@author spike`、`@since 1.0.0` |
| 测试 | 纯单测不启动 Spring 容器 |

### 命名约定

| 类别 | 模式 | 示例 |
|---|---|---|
| Command | `{动作}{对象}Command` | `LoginCommand` |
| Service | `{动作}{对象}ApplicationService` | `LoginApplicationService` |
| UseCase | `{动作}{对象}UseCase` | `LoginUseCase` |
| Result | `{对象}Result` | `CurrentUserResult` |
| Port | `{功能描述}Repository` | `AuthorizationGrantRepository` |
| Jdbc 实现 | `Jdbc{接口名}` | `JdbcAuthorizationGrantRepository` |
| Controller | `{领域}Controller` | `AuthController` |

## 监控端点

```bash
# Actuator 端点
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info
curl http://localhost:8080/actuator/metrics

# Ingest 指标
curl http://localhost:8080/actuator/metrics/myai.ingest.process.success.total
curl http://localhost:8080/actuator/metrics/myai.ingest.process.failed.total
curl http://localhost:8080/actuator/metrics/myai.ingest.process.retry_scheduled.total
curl http://localhost:8080/actuator/metrics/myai.ingest.delete.success.total
curl http://localhost:8080/actuator/metrics/myai.ingest.delete.conflict.total
```

## Docker Compose 基础设施

| 服务 | 镜像 | 端口 | 说明 |
|---|---|---|---|
| postgres | `pgvector/pgvector:pg16` | 5432 | 数据库 + 向量扩展 |
| rustfs | `rustfs/rustfs:latest` | 9000 (API), 9001 (Console) | S3 兼容对象存储 |

- 数据库：`myai`，用户/密码：`admin/admin`
- RustFS 凭证：`admin/Admin@123`
- 时区：`Asia/Shanghai`

## CI/CD

当前未配置 CI/CD 流水线。

---

_生成时间: 2026-06-15 | 扫描模式: 深度扫描_
