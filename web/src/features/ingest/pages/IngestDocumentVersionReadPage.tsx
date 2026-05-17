import {
	ArrowLeftOutlined,
	ExpandAltOutlined,
	RetweetOutlined,
	ShrinkOutlined,
} from "@ant-design/icons";
import { Button, Segmented, Select, Space, Tag, Tooltip, Skeleton, Result } from "antd";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getDocumentVersionHistory, type DocumentVersionHistoryItem } from "../../../shared/api/ingestApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import "./IngestDocumentVersionReadPage.css";

type ReadMode = "single" | "compare";

function buildContentMarkdown(version: DocumentVersionHistoryItem): string {
  // 由于目前后端没有提供获取文档全文的接口，我们使用真实的版本元数据来组装占位文本
	return `
# ${version.filename}
## 第一章：总则
当前正在阅读 **v${version.versionNumber}**。本系统通过 RAG 技术实现了对该文档的深度解析。这个阅读界面旨在提供沉浸式的长内容查阅体验，移除了所有非必要的管理干扰。

## 第二章：关键条款
> 注意：此版本更新于 ${version.updatedAt}，当前状态为 \`${version.status}\`。

1. **版本溯源**：本版本属于 \`${version.versionOriginType}\` 类型。
2. **处理逻辑**：系统已对正文完成了分块处理，目前可支持语义检索与引用溯源。
3. **内容完整性**：对于 MD格式的渲染，我们保持了原汁原样的层级结构，确保阅读时的空间感与逻辑感。

## 第三章：结语
如果您在阅读过程中发现任何解析错误，请使用详情页的“重处理”功能。
  `;
}

function ReaderPane({
	version,
	isLatest,
	isAskable,
  onVersionChange,
  versionOptions
}: {
	version: DocumentVersionHistoryItem;
	isLatest: boolean;
	isAskable: boolean;
  onVersionChange: (v: number) => void;
  versionOptions: any[];
}) {
	return (
		<div className="read-pane">
			<div className="read-pane__header">
				<div className="read-pane__title">
					<span className="read-pane__version-tag">v{version.versionNumber}</span>
          <Space>
					  {isLatest && <Tag color="gold" bordered={false}>LATEST</Tag>}
					  {isAskable && <Tag color="green" bordered={false}>BASE</Tag>}
          </Space>
				</div>
				<Select
          className="read-version-select"
          size="small"
          value={version.versionNumber}
          options={versionOptions}
          onChange={onVersionChange}
          placeholder="切换版本"
        />
			</div>
			<div className="read-pane__scroller">
				<article className="read-content">
          <section dangerouslySetInnerHTML={{ __html: buildContentMarkdown(version).replace(/\n/g, '<br/>').replace(/# (.*?)<br\/>/, '<h1>$1</h1>').replace(/## (.*?)<br\/>/g, '<h2>$1</h2>').replace(/> (.*?)<br\/>/g, '<blockquote>$1</blockquote>').replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>').replace(/`([^`]+)`/g, '<code>$1</code>') }} />
				</article>
			</div>
		</div>
	);
}

export function IngestDocumentVersionReadPage() {
	const navigate = useNavigate();
	const { documentId = "", versionNumber = "" } = useParams<{
		documentId?: string;
		versionNumber?: string;
	}>();
	const [searchParams] = useSearchParams();

	const mode = (searchParams.get("mode") as ReadMode | null) ?? "single";
	
  const historyQuery = useQuery({
		queryKey: ["document-version-history", documentId],
		queryFn: () => getDocumentVersionHistory(documentId),
		enabled: documentId.length > 0,
	});

  const versions = historyQuery.data?.versions ?? [];
  const latestVersion = versions.find((v) => v.isLatestVersion);

	const leftVersionNumber = Number(versionNumber);
	const rightVersionNumber = Number(
		searchParams.get("right") ??
			(leftVersionNumber === latestVersion?.versionNumber
				? (versions[1]?.versionNumber ?? leftVersionNumber)
				: latestVersion?.versionNumber)
	);

	const leftVersion = versions.find((item) => item.versionNumber === leftVersionNumber) ?? versions[0];
	const rightVersion = versions.find((item) => item.versionNumber === rightVersionNumber) ?? versions[0];

	const versionOptions = versions.map((v) => ({
		label: `v${v.versionNumber} (${v.status})`,
		value: v.versionNumber,
	}));

	const updateParams = (next: { left?: number; right?: number; mode?: ReadMode }) => {
    const newLeft = next.left ?? leftVersionNumber;
    const newMode = next.mode ?? mode;
    const newRight = next.right ?? rightVersionNumber;

    const params = new URLSearchParams();
    params.set("mode", newMode);
    if (newMode === "compare") params.set("right", String(newRight));

    navigate(`/ingest/documents/${encodeURIComponent(documentId)}/versions/${newLeft}/read?${params.toString()}`);
	};

  if (historyQuery.isLoading) {
		return (
			<div className="read-page" style={{ padding: 48 }}>
				<Skeleton active paragraph={{ rows: 10 }} />
			</div>
		);
	}

  if (historyQuery.isError) {
		return (
			<div className="read-page" style={{ padding: 48 }}>
				<ApiErrorAlert error={historyQuery.error} />
			</div>
		);
	}

  if (!leftVersion) {
		return (
			<div className="read-page" style={{ padding: 48 }}>
				<Result
					status="info"
					title="未找到可阅读的版本内容"
					extra={
						<Button onClick={() => navigate(`/ingest/documents/${encodeURIComponent(documentId)}`)}>返回详情页</Button>
					}
				/>
			</div>
		);
	}

	return (
		<div className="read-page">
			<header className="read-toolbar">
				<div className="read-toolbar__left">
					<Tooltip title="返回详情页">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate(`/ingest/documents/${encodeURIComponent(documentId)}?version=${leftVersionNumber}`)}
            />
          </Tooltip>
					<div className="read-toolbar__title">
						<span className="read-toolbar__kicker">Document Reader</span>
						<span className="read-toolbar__filename">{leftVersion.filename}</span>
					</div>
				</div>

				<div className="read-toolbar__right">
					<Segmented
						value={mode}
						onChange={(val) => updateParams({ mode: val as ReadMode })}
						options={[
							{ label: "单版阅读", value: "single", icon: <ShrinkOutlined /> },
							{ label: "版本比对", value: "compare", icon: <ExpandAltOutlined /> },
						]}
					/>
          {mode === "compare" && (
            <Button 
              icon={<RetweetOutlined />} 
              onClick={() => updateParams({ left: rightVersionNumber, right: leftVersionNumber })}
            >
              交换
            </Button>
          )}
				</div>
			</header>

			<main className={`read-viewport ${mode === "compare" ? "is-compare" : ""}`}>
				<ReaderPane
					version={leftVersion}
					isLatest={leftVersion.isLatestVersion}
					isAskable={leftVersion.isAskableVersion}
          versionOptions={versionOptions}
          onVersionChange={(v) => updateParams({ left: v })}
				/>
				{mode === "compare" && rightVersion && (
					<ReaderPane
						version={rightVersion}
						isLatest={rightVersion.isLatestVersion}
						isAskable={rightVersion.isAskableVersion}
            versionOptions={versionOptions}
            onVersionChange={(v) => updateParams({ right: v })}
					/>
				)}
			</main>
		</div>
	);
}
