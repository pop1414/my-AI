import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button, Segmented, Select, type SelectProps, Space, Tag, Tooltip, Skeleton, Result, Spin } from "antd";
import ReactMarkdown from "react-markdown";
import type { Components } from "react-markdown";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import remarkGfm from "remark-gfm";
import {
	ArrowLeftOutlined,
	ExpandAltOutlined,
	RetweetOutlined,
	ShrinkOutlined,
  FolderOpenOutlined,
  LoadingOutlined,
} from "@ant-design/icons";
import { getDocumentVersionHistory, getDocumentStatus, getDocumentContent, type DocumentVersionHistoryItem } from "../../../shared/api/ingestApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import "./IngestDocumentVersionReadPage.css";

type ReadMode = "single" | "compare";
type VersionOption = NonNullable<SelectProps<number>["options"]>[number];
type ContentSection =
	| { kind: "markdown"; content: string }
	| { kind: "html"; content: string };
type MathRendererOptions = {
	delimiters: Array<{
		left: string;
		right: string;
		display: boolean;
	}>;
	throwOnError?: boolean;
};

declare global {
	interface Window {
		renderMathInElement?: (element: HTMLElement, options: MathRendererOptions) => void;
	}
}

type DocumentImageProps = {
	alt?: string;
	src?: string;
	title?: string;
};

const HTML_SECTION_PATTERN =
	/<(table|figure|div|p|h[1-6]|blockquote|pre|ul|ol)[^>]*>[\s\S]*?<\/\1>|<img\b[^>]*\/?>|<hr\b[^>]*\/?>|<br\s*\/?>/gi;
const ALLOWED_HTML_TAGS = new Set([
	"a",
	"blockquote",
	"br",
	"code",
	"div",
	"em",
	"figure",
	"h1",
	"h2",
	"h3",
	"h4",
	"h5",
	"h6",
	"hr",
	"img",
	"li",
	"ol",
	"p",
	"pre",
	"strong",
	"table",
	"tbody",
	"td",
	"th",
	"thead",
	"tr",
	"ul",
]);
const ALLOWED_HTML_ATTRIBUTES = new Map<string, Set<string>>([
	["a", new Set(["href", "title"])],
	["img", new Set(["src", "alt", "title", "width", "height"])],
	["td", new Set(["colspan", "rowspan"])],
	["th", new Set(["colspan", "rowspan"])],
]);
const EQ_TAG_PATTERN = /<eq>([\s\S]*?)<\/eq>/gi;
function normalizeSourceContent(content: string): string {
	return content.replace(EQ_TAG_PATTERN, (_, formula: string) => `\\(${formula.trim()}\\)`);
}

function DocumentImage({ alt, src, title }: DocumentImageProps) {
	const [hasError, setHasError] = useState(false);

	if (!src || hasError) {
		return (
			<span className="read-image read-image--fallback">
				{alt || "图片加载失败"}
				{src ? (
					<>
						{" "}
						<a href={src} target="_blank" rel="noreferrer">
							打开原图
						</a>
					</>
				) : null}
			</span>
		);
	}

	return (
		<img
			alt={alt ?? ""}
			src={src}
			title={title}
			loading="lazy"
			referrerPolicy="no-referrer"
			onError={() => setHasError(true)}
		/>
	);
}

function isSafeHtmlUrl(value: string): boolean {
	const trimmed = value.trim();
	if (trimmed.length === 0) {
		return false;
	}
	if (/^(javascript|vbscript|file):/i.test(trimmed)) {
		return false;
	}
	if (/^data:/i.test(trimmed)) {
		return /^data:image\//i.test(trimmed);
	}
	return true;
}

function sanitizeHtmlFragment(fragment: string): string {
	if (typeof DOMParser === "undefined") {
		return "";
	}

	const parser = new DOMParser();
	const document = parser.parseFromString(`<body>${fragment}</body>`, "text/html");
	const elements = Array.from(document.body.querySelectorAll("*"));

	for (const element of elements) {
		const tagName = element.tagName.toLowerCase();
		if (!ALLOWED_HTML_TAGS.has(tagName)) {
			element.replaceWith(...Array.from(element.childNodes));
			continue;
		}

		for (const attribute of Array.from(element.attributes)) {
			const attributeName = attribute.name.toLowerCase();
			const allowedAttributes = ALLOWED_HTML_ATTRIBUTES.get(tagName) ?? new Set<string>();
			if (!allowedAttributes.has(attributeName)) {
				element.removeAttribute(attribute.name);
				continue;
			}
			if ((attributeName === "href" || attributeName === "src")
				&& !isSafeHtmlUrl(attribute.value)) {
				element.removeAttribute(attribute.name);
			}
		}
		if (tagName === "img") {
			element.setAttribute("loading", "lazy");
			element.setAttribute("referrerpolicy", "no-referrer");
		}
	}

	return document.body.innerHTML;
}

function splitContentSections(content: string): ContentSection[] {
	if (!content) {
		return [{ kind: "markdown", content: "" }];
	}

	const normalizedContent = normalizeSourceContent(content);
	const sections: ContentSection[] = [];
	let lastIndex = 0;
	HTML_SECTION_PATTERN.lastIndex = 0;
	let match = HTML_SECTION_PATTERN.exec(normalizedContent);

	while (match) {
		const [htmlFragment] = match;
		if (match.index > lastIndex) {
			const markdownFragment = normalizedContent.slice(lastIndex, match.index);
			if (markdownFragment.trim().length > 0) {
				sections.push({ kind: "markdown", content: markdownFragment });
			}
		}
		sections.push({
			kind: "html",
			content: sanitizeHtmlFragment(htmlFragment),
		});
		lastIndex = match.index + htmlFragment.length;
		match = HTML_SECTION_PATTERN.exec(normalizedContent);
	}

	if (lastIndex < normalizedContent.length) {
		const markdownFragment = normalizedContent.slice(lastIndex);
		if (markdownFragment.trim().length > 0 || sections.length === 0) {
			sections.push({ kind: "markdown", content: markdownFragment });
		}
	}

	HTML_SECTION_PATTERN.lastIndex = 0;
	return sections;
}

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
  versionOptions: VersionOption[];
  documentId: string;
  source?: "LATEST" | "ASKABLE_BASELINE" | "EXPLICIT_VERSION";
}) {
  const markdownComponents = useMemo<Components>(
    () => ({
      img: ({ node: _node, ...props }) => <DocumentImage {...props} />,
    }),
    [],
  );
  const contentQuery = useQuery({
    queryKey: ["document-content", documentId, version.versionNumber],
    queryFn: () => getDocumentContent({
      documentId,
      source,
      versionNumber: version.versionNumber
    }),
    enabled: documentId.length > 0 && version.versionNumber > 0,
  });
  const contentContainerRef = useRef<HTMLElement | null>(null);
  const contentSections = useMemo(
    () => splitContentSections(contentQuery.data?.contentMarkdown ?? ""),
    [contentQuery.data?.contentMarkdown],
  );

  useEffect(() => {
    let retryTimer: number | undefined;
    let retryCount = 0;

    const renderMath = () => {
      const container = contentContainerRef.current;
      const renderMathInElement = window.renderMathInElement;
      if (!container) {
        return;
      }
      if (!renderMathInElement) {
        if (retryCount < 10) {
          retryCount += 1;
          retryTimer = window.setTimeout(renderMath, 200);
        }
        return;
      }

      renderMathInElement(container, {
        delimiters: [
          { left: "$$", right: "$$", display: true },
          { left: "\\[", right: "\\]", display: true },
          { left: "$", right: "$", display: false },
          { left: "\\(", right: "\\)", display: false },
        ],
        throwOnError: false,
      });
    };

    renderMath();
    return () => {
      if (retryTimer !== undefined) {
        window.clearTimeout(retryTimer);
      }
    };
  }, [contentSections]);

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
				  <article className="read-content" ref={contentContainerRef}>
            {contentSections.map((section, index) =>
              section.kind === "html" ? (
                <div
                  key={`html-${index}`}
                  dangerouslySetInnerHTML={{ __html: section.content }}
                />
              ) : (
                <ReactMarkdown
                  key={`markdown-${index}`}
                  remarkPlugins={[remarkGfm]}
                  components={markdownComponents}
                >
                  {section.content}
                </ReactMarkdown>
              ),
            )}
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
