import {
	ArrowLeftOutlined,
	ExpandAltOutlined,
	RetweetOutlined,
	ShrinkOutlined,
} from "@ant-design/icons";
import { Button, Segmented, Select, Space, Tag, Tooltip } from "antd";
import { useMemo } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
	buildDocumentDetailMockData,
	type PrototypeVersion,
} from "./documentDetailMockData";
import "./IngestDocumentVersionReadPage.css";

type ReadMode = "single" | "compare";

function ReaderPane({
	version,
	isLatest,
	isAskable,
  onVersionChange,
  versionOptions
}: {
	version: PrototypeVersion;
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
          <h1>{version.filename}</h1>
          <section dangerouslySetInnerHTML={{ __html: `
            <h2>第一章：总则</h2>
            <p>当前正在阅读 <strong>v${version.versionNumber}</strong>。本系统通过 RAG 技术实现了对该文档的深度解析。这个阅读界面旨在提供沉浸式的长内容查阅体验，移除了所有非必要的管理干扰。</p>
            <h2>第二章：关键条款</h2>
            <blockquote>注意：此版本创建于 ${version.createdAt}，当前状态为 ${version.status}。</blockquote>
            <ul>
              <li><strong>版本溯源</strong>：本版本属于 ${version.versionOriginType} 类型。</li>
              <li><strong>处理逻辑</strong>：系统已对正文完成了分块处理，目前可支持语义检索与引用溯源。</li>
              <li><strong>内容完整性</strong>：对于 MD格式的渲染，我们保持了原汁原样的层级结构，确保阅读时的空间感与逻辑感。</li>
            </ul>
            <h2>第三章：结语</h2>
            <p>如果您在阅读过程中发现任何解析错误，请使用详情页的“重处理”功能。</p>
          ` }} />
				</article>
			</div>
		</div>
	);
}

export function IngestDocumentVersionReadPage() {
	const navigate = useNavigate();
	const { documentId = "doc-prototype-001", versionNumber = "4" } = useParams<{
		documentId?: string;
		versionNumber?: string;
	}>();
	const [searchParams] = useSearchParams();

	const mode = (searchParams.get("mode") as ReadMode | null) ?? "single";
	const document = useMemo(() => buildDocumentDetailMockData(documentId), [documentId]);
	const leftVersionNumber = Number(versionNumber);
	const rightVersionNumber = Number(
		searchParams.get("right") ??
			(leftVersionNumber === document.latestVersionNumber
				? (document.versions[1]?.versionNumber ?? leftVersionNumber)
				: document.latestVersionNumber)
	);

	const leftVersion = document.versions.find((item) => item.versionNumber === leftVersionNumber) ?? document.versions[0]!;
	const rightVersion = document.versions.find((item) => item.versionNumber === rightVersionNumber) ?? document.versions[0]!;

	const versionOptions = document.versions.map((v) => ({
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
					isLatest={leftVersion.versionNumber === document.latestVersionNumber}
					isAskable={leftVersion.versionNumber === document.askableVersionNumber}
          versionOptions={versionOptions}
          onVersionChange={(v) => updateParams({ left: v })}
				/>
				{mode === "compare" && (
					<ReaderPane
						version={rightVersion}
						isLatest={rightVersion.versionNumber === document.latestVersionNumber}
						isAskable={rightVersion.versionNumber === document.askableVersionNumber}
            versionOptions={versionOptions}
            onVersionChange={(v) => updateParams({ right: v })}
					/>
				)}
			</main>
		</div>
	);
}
