import { ArrowLeftOutlined, FolderOpenOutlined } from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { Button, Result, Skeleton, Tag, Tooltip } from "antd";
import { useNavigate, useParams } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { getDocumentContent, getDocumentStatus } from "../../../shared/api/ingestApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import "./MemberAskableBaselinePage.css";

export function MemberAskableBaselinePage() {
	const { documentId = "" } = useParams<{ documentId?: string }>();
	const navigate = useNavigate();
	
	const contentQuery = useQuery({
		queryKey: ["member-document-content", documentId, "ASKABLE_BASELINE"],
		queryFn: () =>
			getDocumentContent({
				documentId,
				source: "ASKABLE_BASELINE",
			}),
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
	const knowledgeBase = kbId ? kbQuery.data?.find((kb) => kb.id === kbId) : undefined;
	const knowledgeBaseName = knowledgeBase?.name ?? kbId;

	if (contentQuery.isLoading || statusQuery.isLoading) {
		return (
			<div className="member-read-page">
				<header className="member-read-toolbar">
					<Skeleton.Button active size="small" style={{ width: 100 }} />
				</header>
				<main className="member-read-viewport">
					<div className="member-read-scroller">
						<div className="member-read-content">
							<Skeleton active paragraph={{ rows: 20 }} />
						</div>
					</div>
				</main>
			</div>
		);
	}

	if (contentQuery.isError || !contentQuery.data) {
		return (
			<div className="member-read-page">
				<Result
					status="error"
					title="无法加载问答基线"
					subTitle="该文档可能尚未设置问答基线，或当前账号无权访问。"
					extra={<Button onClick={() => navigate("/ingest/documents")}>返回目录</Button>}
				/>
			</div>
		);
	}

	return (
		<div className="member-read-page">
			<header className="member-read-toolbar">
				<div className="member-read-toolbar__left">
					<Tooltip title="返回目录">
						<Button
							type="text"
							icon={<ArrowLeftOutlined />}
							onClick={() => navigate("/ingest/documents")}
						/>
					</Tooltip>
					<div className="member-read-toolbar__title">
						<span className="member-read-toolbar__kicker">Baseline Reader</span>
						<div style={{ display: "flex", alignItems: "center", gap: 12 }}>
							<span className="member-read-toolbar__filename">{contentQuery.data.filename}</span>
							{knowledgeBaseName && (
								<Tag icon={<FolderOpenOutlined />} bordered={false} style={{ margin: 0, opacity: 0.8 }}>
									{knowledgeBaseName}
								</Tag>
							)}
						</div>
					</div>
				</div>
			</header>

			<main className="member-read-viewport">
				<div className="member-read-scroller">
					<article className="member-read-content">
						<ReactMarkdown remarkPlugins={[remarkGfm]}>
							{contentQuery.data.contentMarkdown}
						</ReactMarkdown>
					</article>
				</div>
			</main>
		</div>
	);
}
