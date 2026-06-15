# Docling 开发服务

一款为**开发与测试场景**提供 Docling 服务实例的托管服务。

该服务可与支持 Docling 的 Spring Boot 组件库配合使用，包括：

- 阿尔科尼亚 Docling
- 阿尔科尼亚 AI Docling 文档读取器

---

## 依赖配置

首先，你需要将开发服务依赖添加到项目中。

### Maven 配置

```xml
<dependency>
    <groupId>io.arconia</groupId>
    <artifactId>arconia-dev-services-docling</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

### 可选依赖：Spring Boot 开发工具

你可以额外引入 Spring Boot 开发工具依赖，以在开发过程中实现**应用热重载**。

#### Maven 配置

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

## 运行应用

使用 Arconia 开发服务时，你可按照常规方式启动应用，**开发服务会随应用自动启动**。

### 启动命令

- Maven：./mvnw spring-boot:run
- 命令行：arconia dev

与 Spring Boot 底层的**测试容器（Testcontainers）** 支持不同：使用本开发服务时，Arconia 无需通过特殊任务启动应用（如 `./gradlew bootTestRun` 或 `./mvnw spring-boot:test-run`），也无需为配置测试容器单独定义 `@SpringBootApplication` 类。

你的集成测试会**自动使用该开发服务**，无需任何额外配置。

默认情况下，以**开发模式**运行应用时，该开发服务可被多个应用共享。

此外，开发服务默认启用 **Docling 可视化界面（UI）**，应用日志中会打印该界面的访问地址：

```
... Docling UI: http://localhost:<端口>/ui
```

---

## 开发服务配置

你可以通过**配置属性**自定义开发服务参数，配置项说明如下：

| 属性                                         | 默认值                                | 描述                                                                                                       |
| :------------------------------------------- | :------------------------------------ | :--------------------------------------------------------------------------------------------------------- |
| arconia.dev.services.docling.enabled         | true                                  | 是否启用该开发服务                                                                                         |
| arconia.dev.services.docling.image-name      | ghcr.io/docling-project/docling-serve | 开发服务使用的容器镜像完整名称                                                                             |
| arconia.dev.services.docling.environment     | {}                                    | 服务容器中配置的环境变量                                                                                   |
| arconia.dev.services.docling.network-aliases | []                                    | 分配给开发服务容器的网络别名                                                                               |
| arconia.dev.services.docling.port            | 0                                     | 向主机暴露的 Docling 服务 HTTP 固定端口；默认值 0 表示**动态分配随机可用端口**                             |
| arconia.dev.services.docling.resources       | []                                    | 需从类路径/主机文件系统复制到容器内的资源（文件/目录）；启动时复制到容器指定路径，为**只读不可变**         |
| arconia.dev.services.docling.shared          | true                                  | 开发服务是否在多个应用间共享（仅适用于开发模式）                                                           |
| arconia.dev.services.docling.startup-timeout | 30s                                   | 服务启动的最大等待超时时间                                                                                 |
| arconia.dev.services.docling.volumes         | []                                    | 需从主机挂载到容器内的文件/目录；启动时挂载到容器指定路径，为**读写可变**（主机/容器的修改会实时双向同步） |
| arconia.dev.services.docling.enable-ui       | true                                  | 开发模式下是否启用 Docling 可视化界面                                                                      |
| arconia.dev.services.docling.api-key         | 空                                    | 用于 Docling 服务 API 请求身份验证的密钥                                                                   |

---

你可以依托 Arconia 自动配置的**环境配置文件**（详见配置文件章节），针对**开发/测试**等特定应用模式，选择性启用/禁用开发服务。

你也可以通过 `@TestProperty` 注解或 Spring 等效测试工具，为**单个测试类**单独启用/禁用开发服务。
