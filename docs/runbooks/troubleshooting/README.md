# 排障手册导航

`docs/runbooks/troubleshooting/` 用来放可执行的排障与恢复手册。

这里的文档应该回答：

- 怎么识别异常状态
- 怎么确认影响范围
- 哪些操作需要先停止服务或 worker
- 如何人工恢复
- 恢复后怎么验证

## 当前文件

- [ingest-document-asset-recovery.md](./ingest-document-asset-recovery.md)：文档入库资产恢复手册，覆盖源文件、处理产物、向量数据和数据库状态不一致的排查与恢复。
