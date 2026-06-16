import {
	ArrowLeftOutlined,
	ExpandAltOutlined,
	RetweetOutlined,
	ShrinkOutlined,
  FolderOpenOutlined,
  LoadingOutlined,
} from "@ant-design/icons";
import { Button, Segmented, Select, Space, Tag, Tooltip, Skeleton, Result, Spin } from "antd";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getDocumentVersionHistory, getDocumentStatus, getDocumentContent, type DocumentVersionHistoryItem } from "../../../shared/api/ingestApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import "./IngestDocumentVersionReadPage.css";

type ReadMode = "single" | "compare";

function ReaderPane({
	version,
	isLatest,
	isAskable,
  onVersionChange,
  versionOptions,
  documentId,
  source = "EXPLICIT_VERSION"
}: {
	version: DocumentVersionHistoryItem;
	isLatest: boolean;
	isAskable: boolean;
  onVersionChange: (v: number) => void;
  versionOptions: Array<{ label: string; value: number }>;
  documentId: string;
  source?: "LATEST" | "ASKABLE_BASELINE" | "EXPLICIT_VERSION";
}) {
  const contentQuery = useQuery({
    queryKey: ["document-content", documentId, version.versionNumber],
    queryFn: () => getDocumentContent({
      documentId,
      source,
      versionNumber: version.versionNumber
    }),
    enabled: documentId.length > 0 && version.versionNumber > 0,
  });

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
        {contentQuery.isLoading ? (
          <div style={{ padding: 48, textAlign: 'center' }}><Spin indicator={<LoadingOutlined style={{ fontSize: 24 }} spin />} /></div>
        ) : contentQuery.isError ? (
          <div style={{ padding: 48 }}><ApiErrorAlert error={contentQuery.error} /></div>
        ) : (
				  <article className="read-content">
            <section dangerouslySetInnerHTML={{ __html: contentQuery.data?.contentMarkdown.replace(/\n/g, '<br/>').replace(/# (.*?)<br\/>/, '<h1>$1</h1>').replace(/## (.*?)<br\/>/g, '<h2>$1</h2>').replace(/> (.*?)<br\/>/g, '<blockquote>$1</blockquote>').replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>').replace(/`([^`]+)`/g, '<code>$1</code>') ?? "" }} />
				  </article>
        )}
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

  const statusQuery = useQuery({
    queryKey: ["document-status", documentId],
    queryFn: () => getDocumentStatus(documentId),
    enabled: documentId.length > 0,
  });

  const kbQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});

  const kbId = statusQuery.data?.kbId;
  const knowledgeBase = kbId ? kbQuery.data?.find(kb => kb.id === kbId) : undefined;
  const knowledgeBaseName = knowledgeBase?.name ?? kbId;
  const knowledgeBaseIdLabel =
    knowledgeBase && knowledgeBase.name !== knowledgeBase.id
      ? knowledgeBase.id
      : undefined;

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
            <Space align="center" size="middle">
						  <span className="read-toolbar__filename">{leftVersion.filename}</span>
              {knowledgeBaseName && (
                <Tag icon={<FolderOpenOutlined />} bordered={false} style={{ margin: 0, opacity: 0.8 }}>
                  {knowledgeBaseName}
                  {knowledgeBaseIdLabel ? ` (${knowledgeBaseIdLabel})` : ""}
                </Tag>
              )}
            </Space>
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
          documentId={documentId}
          source="EXPLICIT_VERSION"
				/>
				{mode === "compare" && rightVersion && (
					<ReaderPane
						version={rightVersion}
						isLatest={rightVersion.isLatestVersion}
						isAskable={rightVersion.isAskableVersion}
            versionOptions={versionOptions}
            onVersionChange={(v) => updateParams({ right: v })}
            documentId={documentId}
            source="EXPLICIT_VERSION"
					/>
				)}
			</main>
		</div>
	);
}
