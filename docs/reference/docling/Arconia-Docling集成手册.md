# Arconia Docling

Arconia 与 Docling 实现**无缝集成**。Docling 是一款强大的AI驱动文档转换服务，可将文档转换为 Markdown 等结构化格式。该集成方案基于 Docling Java 项目构建，提供**自动配置的 `DoclingServeApi`**，可直接在 Spring Boot 应用中调用 Docling 服务API，完成 PDF、Word 文档、网页等多种格式的文档转换。

## 快速开始

下文将介绍如何在 Spring Boot 应用中快速接入 Arconia Docling。

### 依赖配置

为 Spring Boot 应用添加 Docling 支持，需引入 Arconia Docling Spring Boot 启动器依赖。

#### Maven 配置

```xml
<dependency>
    <groupId>io.arconia</groupId>
    <artifactId>arconia-docling-spring-boot-starter</artifactId>
</dependency>
```

Arconia 官方发布了**物料清单（BOM）**，用于统一管理 Arconia 所有类库的版本。虽非强制要求，但**推荐使用 BOM** 以确保所有依赖版本兼容。

#### Maven BOM 配置

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.arconia</groupId>
            <artifactId>arconia-bom</artifactId>
            <version>0.27.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 开发服务

Arconia 开发服务依托 **Testcontainers（测试容器）** 和 Spring Boot 的能力，为应用依赖的第三方服务提供**开发/测试环境双支持的零代码集成**。

使用 Docling 时，可搭配 Docling 开发服务：**在开发和测试阶段自动启动 Docling 服务实例**，无需手动部署即可完成文档转换。

如需启用 Docling 开发服务，在项目中添加以下依赖：

#### Maven 配置

```xml
<dependency>
    <groupId>io.arconia</groupId>
    <artifactId>arconia-dev-services-docling</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

开发服务默认会在指定端口暴露 Docling 服务可视化界面，应用日志会打印访问地址：

```
... Docling Serve UI: http://localhost:<端口>/ui
```

## 运行应用

使用 Arconia 开发服务时，**按常规方式启动应用即可**，开发服务会随应用自动启动。

### 启动命令

- Maven：./mvnw spring-boot:run
- 命令行：arconia dev

与 Spring Boot 底层的测试容器支持不同：使用本开发服务时，Arconia 无需通过特殊任务启动应用（如 `./gradlew bootTestRun` 或 `./mvnw spring-boot:test-run`），也无需为配置测试容器单独定义 `@SpringBootApplication` 类。

应用日志会打印 Docling 服务界面地址，可直接在界面上交互式完成文档转换。

## 配置项

Arconia Docling 集成提供了连接 Docling 服务API的默认配置，你可通过配置属性自定义连接参数与超时时间。

### 表1 Docling 客户端配置属性

| 属性                            | 默认值                | 描述                               |
| :------------------------------ | :-------------------- | :--------------------------------- |
| arconia.docling.base-url        | http://localhost:5001 | Docling 服务API的基础地址          |
| arconia.docling.api-key         | 空                    | 调用 Docling 服务API的身份验证密钥 |
| arconia.docling.connect-timeout | 5s                    | 连接 Docling 服务API的超时时间     |
| arconia.docling.read-timeout    | 30s                   | 接收 Docling 服务API响应的超时时间 |

## 执行器（Actuator）

### 健康指示器

若项目依赖中包含 Spring Boot Actuator，Arconia 会**自动为 Docling 集成配置健康指示器**。该指示器通过调用 Docling 服务的健康检查接口，验证服务连通性，支持通过配置属性自定义。

### 表2 健康检查配置属性

| 属性                              | 默认值 | 描述                        |
| :-------------------------------- | :----- | :-------------------------- |
| management.health.docling.enabled | true   | 是否启用 Docling 健康指示器 |

启用后，健康状态会展示在 Actuator `/health` 接口响应中，用于判断 Docling 服务是否可访问、正常运行。

## 使用 Docling 客户端

引入依赖并完成（可选）配置后，即可在 Spring 组件中**自动注入**并使用自动配置的 `DoclingServeApi`。

### 基础用法

```java
@Component
public class DocumentService {

    private final DoclingServeApi doclingClient;

    // 构造注入Docling客户端
    public DocumentService(DoclingServeApi doclingClient) {
        this.doclingClient = doclingClient;
    }

    // 转换网页为Markdown格式
    public String convertWebPage(String url) {
        ConvertDocumentRequest request = ConvertDocumentRequest.builder()
                .source(HttpSource.builder().url(url).build())
                .build();

        InBodyConvertDocumentResponse response = (InBodyConvertDocumentResponse) doclingServeApi.convertSource(request);
        return response.getDocument().getMarkdownContent();
    }
}
```

### 转换HTTP网络资源

可转换通过 HTTP/HTTPS 地址访问的网页或文档：

```java
ConvertDocumentRequest request = ConvertDocumentRequest.builder()
        .source(HttpSource.builder()
            .url(URI.create("https://docs.arconia.io/arconia/latest/integrations/docling/"))
            .build())
        .build();

InBodyConvertDocumentResponse response = (InBodyConvertDocumentResponse) doclingClient.convertSource(request);
// 获取转换后的Markdown内容
String markdownContent = response.getDocument().getMarkdownContent();
// 获取原文件名
String filename = response.getDocument().getFilename();
```

### 转换本地文件

可将本地文件编码为 Base64 格式后进行转换：

```java
// 读取项目类路径下的PDF文件
byte[] fileContent = new ClassPathResource("document.pdf").getContentAsByteArray();
String base64Content = Base64.getEncoder().encodeToString(fileContent);

ConvertDocumentRequest request = ConvertDocumentRequest.builder()
        .source(FileSource.builder()
            .filename("document.pdf")
            .base64String(base64Content)
            .build())
        .build();

InBodyConvertDocumentResponse response = (InBodyConvertDocumentResponse) doclingClient.convertSource(request);
String markdownContent = response.getDocument().getMarkdownContent();
```

### 自定义转换选项

可通过 `ConvertDocumentOptions` 自定义文档转换规则（如包含图片、开启OCR）：

```java
ConvertDocumentOptions options = ConvertDocumentOptions.builder()
        .includeImages(true) // 包含图片
        .doOcr(true) // 开启光学字符识别
        .build();

ConvertDocumentRequest request = ConvertDocumentRequest.builder()
        .source(HttpSource.builder()
            .url(URI.create("https://docs.arconia.io/arconia/latest/integrations/docling/"))
            .build())
        .options(options)
        .build();

ConvertDocumentResponse response = doclingClient.convertSource(request);
```

### 错误处理

`DoclingServeApi` 会基于底层的 RestClient，针对不同异常场景抛出对应的运行时异常：

```java
try {
    ConvertDocumentRequest request = ConvertDocumentRequest.builder()
            .source(HttpSource.builder()
                .url(URI.create("https://invalid-url.com/document.pdf"))
                .build())
            .build();
    ConvertDocumentResponse response = doclingClient.convertSource(request);
} catch (HttpClientErrorException.NotFound ex) {
    log.warn("文档不存在：{}", ex.getMessage());
} catch (HttpClientErrorException ex) {
    log.error("转换时客户端异常：{}", ex.getMessage());
} catch (HttpServerErrorException ex) {
    log.error("转换时服务端异常：{}", ex.getMessage());
}
```

### 程序式健康检查

也可通过代码主动检查 Docling 服务的运行状态：

```java
HealthCheckResponse health = doclingClient.health();
if ("ok".equals(health.getStatus())) {
    // Docling服务正常
} else {
    // 处理服务异常
}
```
