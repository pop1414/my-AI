## Agent skills

### Issue tracker

GitHub Issues 是本仓库的任务与缺陷跟踪入口。See `docs/agents/issue-tracker.md`.

### Triage labels

使用默认 triage 标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。See `docs/agents/triage-labels.md`.

### Domain docs

Single-context 仓库；领域上下文采用仓库级统一视角，架构决策记录位于 `docs/adr/`。See `docs/agents/domain.md`.

### 代码注释要求

- 注释语言：中文
- 符合阿里巴巴 Java 开发手册的注释规范，尤其是 Javadoc 注释的使用，确保每个类、方法和重要代码块都有清晰的注释说明。
- 前端代码不需要写注释
- 注释内容要清晰、简洁，避免冗长和无意义的描述，重点说明代码的功能、输入输出、边界条件和异常处理等关键信息。
- 注释要与代码保持同步，任何修改代码的同时都要检查相关注释是否需要更新，确保注释内容始终准确反映代码逻辑。

### 改动后要求

每次改动之后，无论是编码还是文档的书写，都要给出对应的commit message
要求：
- 1.标题+body
    例如：
    fix(xxx): xxx
    - xxx
    - xxx
    - xxx
    - ...
- 2.中文
- 3.要详细一点，不要一笔带过，也不要长篇大论，符合企业规范
- 4.如果修改的文件能分批次提交，就遵循分组提交的原则，然后把每批次需要提交的文件列出来，再给出对应的commit message。注意，不要擅自进行git提交，你只需把message写出给我，我进行复制粘贴。前端与后端代码不要同时编码，优先进行后端代码的编写，确保后端代码无误再进行前端代码的编写
- 5.告诉我如何审阅这些代码