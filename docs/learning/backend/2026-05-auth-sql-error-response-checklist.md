# 认证 SQL 与异常响应防坑清单

## 1. 背景 / 问题

这次在 `my-AI` 的认证链路里，连续踩到了两个很典型、也很容易在真实项目里反复出现的问题：

1. PostgreSQL 原生 SQL 在 JDBC 占位符参与表达式时，类型推断和我想的不一致
2. 控制器明明已经抛出了正确的 HTTP 状态码和业务消息，最终响应却被框架默认错误链路改写

这两个问题叠在一起后，表面现象会非常迷惑：

- 后端业务逻辑看起来是对的
- 数据库里的状态也在变化
- 但前端 / Postman 看到的状态码和文案却不对

所以这篇文档不是在讲“怎么实现登录”，而是在沉淀：

> 当项目里同时存在 PostgreSQL 原生 SQL、Spring Boot 默认错误分发、Spring Security 入口点时，我该如何避免踩坑。

## 2. 这次项目里我是怎么遇到它的

### 2.1 第一个坑：`locked_until` 类型推断错误

登录失败计数写入 `login_lock_states` 时，SQL 里有这样一段逻辑：

- 失败次数未达到阈值：`locked_until = NULL`
- 达到阈值：`locked_until = ?`

我最初把它写成了类似：

```sql
CASE WHEN ? <= 1 THEN ? ELSE NULL END
```

从 Java 侧看，传进去的是 `Timestamp`，我以为 PostgreSQL 会自然推断成 `TIMESTAMPTZ`。  
但实际执行时，PostgreSQL 在 `CASE + 占位符 + NULL` 这种表达式里没有按预期推断类型，最终报错：

- 列类型：`timestamp with time zone`
- 表达式类型：`text`

结果是：

- 错密码本来应该只返回 `401`
- 但失败计数写库阶段直接 SQL 报错

### 2.2 第二个坑：锁定逻辑已生效，但响应被改写成通用 401

后面我修好了 SQL，数据库中已经能看到：

- `failed_login_count` 增加
- `locked_until` 正常写入

按业务逻辑，账号已锁定时应该返回：

- `403`
- `account is locked until ...`

但 Postman 和前端仍然看到：

```json
{
  "code": "UNAUTHORIZED",
  "message": "authentication is required"
}
```

最后排查发现，问题不在登录业务本身，而在异常响应链路：

1. `AuthController` 抛出了 `ResponseStatusException`
2. 请求又落入 Spring Boot 默认 `/error` 分发
3. `/error` 这条链路再次经过 Spring Security
4. 最终被 `JsonAuthenticationEntryPoint` 改写成通用 `401`

换句话说：

> 业务层和控制器层想返回的结果是对的，但最终落到客户端时，被框架链路“覆盖”了。

## 3. 我最后采用了什么方案

### 3.1 对 PostgreSQL 表达式里的占位符显式加类型

对于 `locked_until`，我不再依赖数据库隐式推断，而是显式写成：

```sql
CASE
  WHEN ? <= 1 THEN CAST(? AS TIMESTAMPTZ)
  ELSE NULL::TIMESTAMPTZ
END
```

以及：

```sql
WHEN login_lock_states.failed_login_count + 1 >= ? THEN CAST(? AS TIMESTAMPTZ)
```

这样做的好处是：

- SQL 的意图清晰
- PostgreSQL 不需要猜测类型
- 后续维护者看一眼就知道这里是时间字段

### 3.2 用全局 REST 异常处理器截住 `ResponseStatusException`

我新增了 `GlobalRestExceptionHandler`，专门把控制器层显式抛出的 `ResponseStatusException` 直接转换成 JSON：

- 不再让请求进入默认 `/error`
- 不再让 Spring Security 把它改写成通用未登录响应

这样之后：

- 错密码 → `401 + invalid username or password`
- 锁定账号 → `403 + account is locked until ...`
- 禁用账号 → `403 + account is disabled`

### 3.3 前端只做“展示翻译”，不重新发明认证语义

登录页不再自己猜测认证状态，而是根据后端明确返回的状态码和消息做业务化文案映射：

- `401` → 用户名或密码错误
- `403 + account is locked until ...` → 账号已锁定，请于某时后重试
- `403 + account is disabled` → 账号已被禁用，请联系管理员

这里的关键原则是：

> 前端可以翻译用户文案，但不要在后端认证语义不清晰时自己“脑补状态”。

## 4. 为什么不用别的方案

### 4.1 为什么不是继续依赖 PostgreSQL 自动推断

因为自动推断只在简单语句里“看起来可靠”。  
一旦进入：

- `CASE`
- `COALESCE`
- `NULL`
- `JSONB`
- `TIMESTAMPTZ`

再叠加 JDBC `?` 占位符，推断结果就不值得赌了。

结论是：

> 只要参数进入表达式，而不是直接赋值给列，就优先考虑显式 `CAST`。

### 4.2 为什么不是只改前端，把 401 也展示成“账号已锁定”

因为这会把前端和真实后端语义割裂开。

如果后端实际上还是返回：

- `401`
- `authentication is required`

前端即使硬改成“账号已锁定”，也只是掩盖了后端接口真实不对的问题。  
长期看，这会让：

- Postman 测试结果不可信
- 其他客户端无法复用
- 审计与接口行为难以统一理解

### 4.3 为什么不是只看 service 测试通过就算了

因为这次两个问题都说明：

- service 测试只能证明“业务想做什么”
- 不能证明“SQL 能不能在 PostgreSQL 上真实跑通”
- 也不能证明“最终 HTTP 响应会不会被框架改写”

所以关键链路必须至少补两层测试：

1. repository / SQL 级测试
2. controller / MockMvc 级测试

## 5. 防坑清单

### 5.1 PostgreSQL 原生 SQL 清单

遇到以下场景时，优先怀疑类型推断风险：

- `CASE WHEN ... THEN ? ELSE NULL END`
- `COALESCE(?, ...)`
- `?` 出现在 `JSONB`、`TIMESTAMPTZ`、数组、枚举相关表达式中
- `ON CONFLICT DO UPDATE` 里对列做表达式更新，而不是直接 `EXCLUDED.xxx`

建议规则：

1. 参数只要进入表达式，就考虑显式 `CAST`
2. `NULL` 分支也要显式写类型，如 `NULL::TIMESTAMPTZ`
3. PostgreSQL 特定类型尽量写在 SQL 上，不把希望寄托给 JDBC 驱动和数据库猜测

### 5.2 异常响应链路清单

遇到以下场景时，优先怀疑响应被框架覆盖：

- 控制器里抛了 `ResponseStatusException`
- Postman 里看到的却是统一 `/error` 响应
- Security 入口点文案盖过了业务文案
- 状态码和数据库状态不一致

建议规则：

1. 明确哪些异常由控制器直接返回
2. 对 `ResponseStatusException` 提供统一 JSON 处理器
3. 不要让业务异常再掉进默认 `/error` 链路
4. 安全入口点只处理“真正未认证访问受保护资源”的场景

### 5.3 测试清单

认证链路至少覆盖下面几类：

1. 正确登录
2. 错密码但未锁定
3. 达到失败阈值后触发锁定
4. 锁定期内再次尝试登录
5. 锁定期结束后再次登录
6. 账号禁用
7. `/auth/me` 未登录访问

每类测试至少关注：

- 状态码
- 响应体 `code`
- 响应体 `message`
- 数据库状态是否符合预期

## 6. 这件事面试官可能怎么问

### Q1：为什么数据库里已经锁住了，接口还可能返回 401？

可以回答：

因为业务层抛出的异常并不一定就是客户端最终看到的结果。  
如果异常又掉进了 Spring Boot 默认错误分发，再经过 Spring Security，状态码和错误文案都有可能被统一入口点改写。

### Q2：为什么 PostgreSQL 明明字段是时间类型，JDBC 传的也是 `Timestamp`，还会报 text？

可以回答：

问题不在 Java 参数本身，而在 SQL 表达式的上下文。  
当 `?` 出现在 `CASE WHEN ... THEN ? ELSE NULL END` 这种表达式里时，数据库需要为整个表达式推断统一类型；如果推断链条不稳定，就会出现列类型和表达式类型不一致的问题。

### Q3：为什么不能只靠 service 层测试？

可以回答：

因为 service 测试只能验证业务编排意图，但 SQL 是否能在 PostgreSQL 上真实执行、最终 HTTP 响应是否被框架覆盖，都属于更靠近基础设施和运行时行为的问题，必须靠 repository / controller 级测试兜住。

## 7. 我该怎么回答

一句话总结：

> 这次我处理的不是单纯的“登录功能 bug”，而是把认证链路里两个容易被忽视的基础设施问题补齐了：一是 PostgreSQL 表达式中的参数类型显式化，二是控制器异常响应和 Security 默认错误链路的边界收口。

再展开一点可以说：

> 我最后形成了一套固定规则：参数进入 PostgreSQL 表达式就优先显式 `CAST`，控制器显式抛出的 HTTP 异常必须被统一 JSON 处理器接住，认证链路测试不仅测状态码，还要测最终响应体和数据库状态。

## 8. 相关代码 / 正式文档入口

- 登录应用服务：[LoginApplicationService.java](../../../src/main/java/io/github/spike/myai/auth/application/service/LoginApplicationService.java)
- 本地账户仓储：[JdbcLocalAccountRepository.java](../../../src/main/java/io/github/spike/myai/auth/infrastructure/persistence/JdbcLocalAccountRepository.java)
- 全局 REST 异常处理：[GlobalRestExceptionHandler.java](../../../src/main/java/io/github/spike/myai/shared/rest/GlobalRestExceptionHandler.java)
- 认证控制器：[AuthController.java](../../../src/main/java/io/github/spike/myai/auth/interfaces/rest/AuthController.java)
- 认证安全测试：[AuthSecurityBaselineTest.java](../../../src/test/java/io/github/spike/myai/auth/interfaces/rest/AuthSecurityBaselineTest.java)
- 登录应用服务测试：[LoginApplicationServiceTest.java](../../../src/test/java/io/github/spike/myai/auth/application/service/LoginApplicationServiceTest.java)
- 正式接口契约：[api/openapi.yaml](../../api/openapi.yaml)
