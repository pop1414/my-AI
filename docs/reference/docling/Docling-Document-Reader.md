# Docling Document Reader

Spring AI 提供了 **DocumentReader API**，用于构建**数据摄取流水线**，实现文档的加载、解析、分块与存储，为检索增强生成（RAG）等场景提供上下文支撑。

Arconia 提供了该 API 的实现类——**DoclingDocumentReader**，它基于 Docling 引擎处理文档，使其可直接存入向量数据库。该组件构建于 Arconia Docling 之上，可与 Docling 无缝集成；Docling 是一款强大的 AI 驱动文档转换服务，能将各类文档转换为结构化格式。

---

## 快速开始

下文将介绍如何在 Spring AI 应用中快速使用 Docling 文档读取器。

### 依赖配置

首先，在项目中引入 Docling 文档读取器的核心依赖。

#### Maven配置

```xml
<dependency>
    <groupId>io.arconia</groupId>
    <artifactId>arconia-ai-docling-document-reader</artifactId>
</dependency>
```

Arconia 官方发布了**物料清单（BOM）**，可用于统一管理所有 Arconia 类库的版本。虽非强制要求，但**推荐使用 BOM** 以确保所有依赖版本兼容。

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

---

### 开发服务

Arconia 开发服务依托 **Testcontainers（测试容器）** 和 Spring Boot 的能力，为应用依赖的第三方服务提供**零代码集成**，同时支持开发与测试环境。

使用 Docling 文档读取器时，你可以搭配 Docling 开发服务：**在开发和测试阶段自动启动 Docling 服务实例**，无需手动部署 Docling 服务即可完成文档解析。

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

## 使用 Docling 实现文档摄取

以项目中的 `story.pdf` 文件为例，可通过以下代码构建数据摄取流水线：

```java
@Component
public class IngestionPipeline {

    private final DoclingServeApi doclingServeApi;
    private final VectorStore vectorStore;

    // 构造注入 Docling 客户端与向量存储组件
    public IngestionPipeline(DoclingServeApi doclingServeApi, VectorStore vectorStore) {
        this.doclingServeApi = doclingServeApi;
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    void run() {
        // 加载项目类路径下的 PDF 文件
        Resource file = new ClassPathResource("story.pdf");

        // 通过 Docling 文档读取器解析文件
        List<Document> documents = DoclingDocumentReader.builder()
                .doclingServeApi(doclingServeApi)
                .files(file)
                .build()
                .get();

        // 将处理后的文档存入向量数据库
        vectorStore.add(documents);
    }
}
```

### 代码说明

`DoclingDocumentReader` 会通过 Docling 引擎处理文件，将其拆分为更小的文本块，并生成 Spring AI 标准的 `Document` 文档集合。
最终可通过 Spring AI 的 `VectorStore` API 将文档转换为**向量嵌入（Embeddings）**，并存入向量数据库。

如需了解 Spring AI 数据摄取流水线的更多用法，可查阅官方专属文档。

---

## 运行应用

使用 Arconia 开发服务时，**按常规方式启动应用即可**，开发服务会随应用自动启动。

### 启动命令

- Maven：./mvnw spring-boot:run
- 命令行：arconia dev
