# Backlog — 待改进项

_来自 project-context 生成过程中识别的改进需求。按优先级排列，由 BMAD agent 圆桌讨论中提出。_

---

## P0 — 生产事故级风险

### 1. Controller 错误格式一致性保障
- **来源**: Murat（测试架构师）
- **问题**: 无 `@ControllerAdvice` 策略下，N 个 Controller × M 个方法 = N×M 个 catch 点，任何一处遗漏 = 裸 500 泄露堆栈
- **建议**: 写一个 Contract Test，遍历所有 `@RestController` 方法，注入会抛异常的 mock，断言响应结构始终是 `{status, error, message}`
- **影响**: 安全面 — 漏洞扫描器不会帮你测这个

### 2. Flyway 迁移 CI 验证
- **来源**: Murat
- **问题**: 迁移脚本没有 CI 自动验证，生产部署时才发现失败
- **建议**: CI 中跑 `flyway migrate` → `flyway validate` → 回滚验证，必须跑在真实 PostgreSQL 16（不是 H2），因为用了 PGVector 扩展
- **影响**: 生产事故预防

### 3. CI/CD 管线搭建
- **来源**: Winston（架构师）
- **问题**: 无自动化构建和测试门禁，代码质量完全依赖本地人工执行
- **建议**: 搭建 GitHub Actions，至少包含：后端 `mvn verify`、前端 `npm run build`、Flyway 迁移验证
- **影响**: 没有 CI 时所有质量规则的执行靠纪律，CI 是唯一的自动化防线

---

## P1 — 用户可感知质量风险

### 4. Ant Design ConfigProvider getPopupContainer 配置
- **来源**: Winston + Amelia
- **问题**: Modal、Select 等 Portal 组件挂在 `document.body` 上，不在 ConfigProvider 范围内，导致主题丢失
- **建议**: 配置 `getPopupContainer` 把弹层挂到 ConfigProvider 范围内，或用 Ant Design `App` 组件包裹

### 5. 登出顺序修复
- **来源**: Amelia
- **问题**: 如果先清 AuthContext 再清 React Query 缓存，登出瞬间组件重渲染会发出带旧 session 的请求
- **建议**: 确保登出函数先 `queryClient.clear()` 再 `setUser(null)`，顺序不可逆

### 6. 状态驱动的 UI 模式实施
- **来源**: Sally（UX 设计师）
- **问题**: 缺少骨架屏、错误降级页面、空状态引导、按钮 loading 态等体验模式
- **建议**:
  - 列表页用 `<Skeleton>` 替代全页 `<Spin>`
  - 所有异步操作触发按钮加 `loading` 态
  - 空状态用 `<Empty>` + 行动引导文案
  - 添加页面级 React Error Boundary
  - 搜索输入防抖 300-500ms

### 7. React Query queryKey 集中管理
- **来源**: Winston + Amelia
- **问题**: 同一查询在不同组件里用不同 queryKey 结构，导致缓存无法命中
- **建议**: 所有 queryKey 定义集中在 `shared/api/` 层的 hooks 里，组件只调 hook，不允许在组件里直接写 `useQuery` 的 key

### 8. 功能开关（Feature Flags）机制
- **来源**: John（产品经理）
- **问题**: 代码合并 = 用户可见，没有"已合并但未对用户开放"的状态，半成品功能无法保护用户
- **建议**: 建立功能开关机制，每个面向用户的功能变更关联一个开关，未开启的开关下代码路径不执行

### 9. 环境配置模板
- **来源**: Winston + Amelia
- **问题**: 没有 `.env.example` 或配置清单，AI agent 和新开发者不知道需要哪些环境变量
- **建议**: 维护一份包含所有必需和可选环境变量的配置模板，标注哪些有默认值

### 10. 部署回滚方案
- **来源**: John
- **问题**: 手动部署没有标准化回滚步骤，出问题时无法快速恢复
- **建议**: 保留前一个版本的构建产物，回滚操作一个命令完成，部署后 smoke test 验证核心路径

---

## P2 — 开发效率与可维护性

### 11. PGVector 启动健康检查
- **来源**: Murat
- **问题**: PGVector 的 vector 列维度和 embedding 模型配置不匹配时，只有数据写入后才暴露
- **建议**: `@PostConstruct` 健康检查，验证 vector 列 schema（维度、距离度量）与 Spring 配置一致

### 12. Flyway 迁移中 PGVector 维度锁定
- **来源**: Amelia
- **问题**: embedding 模型换维度时，Flyway 需要 `ALTER TABLE ... ALTER COLUMN`，但没有标准化流程
- **建议**: 将 embedding 维度提取为配置常量，Flyway migration 引用同一值，维度变更需要独立 migration

### 13. Session Auth 测试 Fixture
- **来源**: Murat
- **问题**: MockMvc/WebTestClient 需要显式设置 cookie 或用 `@WithMockUser`，每个测试类重复造轮子
- **建议**: 封装 `AuthenticatedRequestPostProcessor`，所有需要认证的测试统一使用

### 14. CSS 自定义属性与 ConfigProvider Token 统一
- **来源**: Amelia
- **问题**: `index.css` 的 `--console-accent` 和 ConfigProvider 的 `colorPrimary` 可能不同步
- **建议**: 选定一个来源作为语义颜色的 single source of truth，避免混用两种来源定义同一语义颜色

### 15. 后端表单校验错误格式与前端对齐
- **来源**: Amelia
- **问题**: `ResponseStatusException` 返回的错误结构与 Ant Design `Form.setFields()` 不对齐，AI agent 会各写各的
- **建议**: 统一错误格式为 `{ field: string, message: string }[]`，前端 catch 后直接 map 到 `setFields`

---

## 待评估

### 16. React Query 前端单元测试基础设施
- **来源**: Murat
- **问题**: 当前无前端单元测试，React Query 的 cache invalidation 和 mutation rollback 是 bug 高发区
- **建议**: 搭建 Vitest + React Testing Library + MSW 基础设施，覆盖 mutation rollback 路径
- **状态**: 待评估 — 需要先搭建前端测试基础

### 17. 响应式策略定义
- **来源**: Sally
- **问题**: 未定义断点策略和响应式布局规则
- **建议**: 定义 sm/md/lg 断点，Table 列优先级排序，窄屏自动折叠侧边栏，表单单列堆叠
- **状态**: 待评估 — 取决于产品是否有移动端需求

### 18. Expand-Contract 数据库迁移策略
- **来源**: John
- **问题**: 破坏性迁移（删列、改类型）一步执行，迁移失败后数据库处于不可用状态
- **建议**: 采用 expand-contract pattern：先加列 → 迁移数据 → 删旧列，分多步 migration
- **状态**: 待评估 — 当前项目规模较小，是否需要此复杂度待定

### 19. 软删除策略
- **来源**: John
- **问题**: AI agent 擅长写 DELETE/TRUNCATE，业务数据默认硬删除无保护
- **建议**: 业务数据默认软删除，物理删除需人工审批，批量操作有上限约束，删除/修改有审计日志
- **状态**: 待评估 — 取决于数据合规要求

### 20. S3 操作可靠性（不依赖 LIST）
- **来源**: Winston
- **问题**: S3 协议不保证 list-after-write 一致性，异步处理流程中 LIST 可能看不到刚上传的文件
- **建议**: 文件操作用 head-object（GET 元数据）做确认，关键流程加重试机制
- **状态**: 待评估

### 21. 乐观更新接口幂等性
- **来源**: Winston
- **问题**: 前端用 `onMutate` 做乐观更新，但后端 POST 接口非幂等，网络抖动会产生重复数据
- **建议**: 支持乐观更新的 mutation 后端接口必须是幂等的，前端 mutation 生成客户端幂等键
- **状态**: 待评估 — 当前暂未实现乐观更新

### 22. PGVector 参数显式决策
- **来源**: Winston
- **问题**: HNSW 索引参数（m、ef_construction）和向量维度都是运行时配置，编译期不检查
- **建议**: 向量维度和索引参数在架构决策文档中显式声明，根据数据量级做 benchmark 后选择 IVFFlat 或 HNSW
- **状态**: 待评估

### 23. 安全 Filter Chain 顺序文档
- **来源**: Winston
- **问题**: Spring Security filter chain 有严格顺序要求，AI agent 添加 filter 时位置错误导致安全漏洞
- **建议**: 安全相关 filter 通过 `SecurityFilterChain` bean 的 `addFilterBefore`/`addFilterAfter` 明确定位，修改时说明 filter 执行顺序图
- **状态**: 待评估

### 24. 测试断言质量审计
- **来源**: Murat
- **问题**: AI 生成的测试断言空洞化（只 assertNotNull、只测不抛异常），给了虚假的安全感
- **建议**: 每个测试必须检查返回值具体字段，异常映射测试矩阵必须包含"未预期异常"行
- **状态**: 待评估 — 可通过代码审查清单落地

### 25. 状态机转换矩阵测试
- **来源**: Murat
- **问题**: AI 测每个状态下的行为，但不测非法状态转换、并发状态转换、状态转换副作用
- **建议**: 每个聚合根的状态转换有显式转换矩阵表，测试覆盖所有合法转换和至少 N 个非法转换
- **状态**: 待评估
