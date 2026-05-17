import { ArrowLeftOutlined } from "@ant-design/icons";
import { useQuery } from "@tanstack/react-query";
import { Button, Result, Spin, Tag, Typography } from "antd";
import { useNavigate, useParams } from "react-router-dom";
import { getDocumentContent } from "../../../shared/api/ingestApi";
import "./MemberReaderPage.css";

function renderBaselineHtml(contentMarkdown: string): string {
	return contentMarkdown
		.replace(/\n/g, "<br/>")
		.replace(/# (.*?)<br\/>/g, "<h1>$1</h1>")
		.replace(/## (.*?)<br\/>/g, "<h2>$1</h2>")
		.replace(/> (.*?)<br\/>/g, "<blockquote>$1</blockquote>")
		.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
		.replace(/`([^`]+)`/g, "<code>$1</code>");
}

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

	if (contentQuery.isLoading) {
		return (
			<div style={{ padding: 80, textAlign: "center" }}>
				<Spin size="large" />
			</div>
		);
	}

	if (contentQuery.isError || !contentQuery.data) {
		return (
			<div style={{ padding: 48 }}>
				<Result
					status="error"
					title="无法加载问答基线"
					subTitle="该文档可能尚未设置问答基线，或当前账号无权访问。"
					extra={<Button onClick={() => navigate("/ingest/documents")}>返回文档目录</Button>}
				/>
			</div>
		);
	}

	return (
		<div className="member-reader-container">
			<header className="member-reader-header">
				<div className="title-area">
					<Button
						type="text"
						icon={<ArrowLeftOutlined />}
						onClick={() => navigate("/ingest/documents")}
					>
						返回目录
					</Button>
					<Typography.Title level={2} className="title" style={{ marginTop: 16 }}>
						{contentQuery.data.filename}
					</Typography.Title>
					<Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
						当前仅展示该文档的问答基线正文，不展示历史版本、处理状态、分块结果及管理操作。
					</Typography.Paragraph>
				</div>
				<Tag color="green" bordered={false}>
					问答基线
				</Tag>
			</header>

			<main className="member-reader-content">
				<article className="editorial-canvas body-text">
					<div
						dangerouslySetInnerHTML={{
							__html: renderBaselineHtml(contentQuery.data.contentMarkdown),
						}}
					/>
				</article>
			</main>
		</div>
	);
}
