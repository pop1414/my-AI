import { Alert, Button, Card, Descriptions, Space, Tag, Typography } from "antd";
import { useMemo } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
	buildDocumentDetailMockData,
	formatFileSize,
	formatTime,
	statusColor,
} from "./documentDetailMockData";
import "./IngestDocumentDetailPage.css";

function DetailDiffSummary({
	viewingVersionNumber,
	latestVersionNumber,
	askableVersionNumber,
	viewingFilename,
}: {
	viewingVersionNumber: number;
	latestVersionNumber: number;
	askableVersionNumber: number;
	viewingFilename: string;
}) {
	return (
		<div className="detail-page__diff-grid">
			<Card size="small" className="detail-page__diff-card">
				<Typography.Text type="secondary">版本关系</Typography.Text>
				<Typography.Title level={5}>
					v{viewingVersionNumber} / latest v{latestVersionNumber}
				</Typography.Title>
				<Typography.Paragraph type="secondary">
					治理主视图与当前查看版本显式拆开，避免把历史查看误读为系统事实。
				</Typography.Paragraph>
			</Card>
			<Card size="small" className="detail-page__diff-card">
				<Typography.Text type="secondary">文件变化</Typography.Text>
				<Typography.Title level={5}>{viewingFilename}</Typography.Title>
				<Typography.Paragraph type="secondary">
					当前阅读版本的来源文件名放在摘要区，帮助用户快速确认“我现在到底在看哪一版”。
				</Typography.Paragraph>
			</Card>
			<Card size="small" className="detail-page__diff-card">
				<Typography.Text type="secondary">问答基线</Typography.Text>
				<Typography.Title level={5}>v{askableVersionNumber}</Typography.Title>
				<Typography.Paragraph type="secondary">
					当最新版本未可问答时，问答仍继续使用最近一个已 INDEXED 的版本。
				</Typography.Paragraph>
			</Card>
		</div>
	);
}

export function IngestDocumentDetailPage() {
	const navigate = useNavigate();
	const { documentId = "doc-prototype-001" } = useParams<{
		documentId?: string;
	}>();
	const [searchParams] = useSearchParams();

	const document = useMemo(
		() => buildDocumentDetailMockData(documentId),
		[documentId],
	);
	const viewingVersionNumber = Number(
		searchParams.get("version") ?? document.latestVersionNumber,
	);
	const viewingVersion =
		document.versions.find(
			(version) => version.versionNumber === viewingVersionNumber,
		) ?? document.versions[0]!;
	const isViewingLatest = viewingVersion.versionNumber === document.latestVersionNumber;

	const openReadPage = (versionNumber: number) => {
		navigate(
			`/ingest/documents/${encodeURIComponent(document.documentId)}/versions/${versionNumber}/read`,
		);
	};

	const backToLatest = () => {
		navigate(`/ingest/documents/${encodeURIComponent(document.documentId)}`);
	};

	return (
		<div className="detail-page">
			<div className="detail-page__hero">
				<div>
					<Typography.Text className="detail-page__kicker">
						document detail / ledger
					</Typography.Text>
					<Typography.Title>{document.title}</Typography.Title>
					<Typography.Paragraph>
						文档列表页继续负责检索与定位；这里是选中某个文档之后的版本治理入口。
					</Typography.Paragraph>
				</div>
				<div className="detail-page__hero-actions">
					<Button onClick={() => navigate("/ingest/documents")}>
						返回文档列表
					</Button>
					<Button type="primary" onClick={() => openReadPage(viewingVersion.versionNumber)}>
						查看该版本内容
					</Button>
				</div>
			</div>

			{!isViewingLatest && (
				<Alert
					type="warning"
					showIcon
					message={`正在查看历史版本 v${viewingVersion.versionNumber}`}
					description="此视图仅用于审计与治理查看，不会改变当前最新版本与问答基线。"
				/>
			)}

			<DetailDiffSummary
				viewingVersionNumber={viewingVersion.versionNumber}
				latestVersionNumber={document.latestVersionNumber}
				askableVersionNumber={document.askableVersionNumber}
				viewingFilename={viewingVersion.filename}
			/>

			<div className="detail-page__grid">
				<div className="detail-page__main">
					<Card className="detail-page__panel detail-page__panel--accent">
						<Typography.Text type="secondary">当前查看版本概览</Typography.Text>
						<Typography.Title level={2}>
							v{viewingVersion.versionNumber}
						</Typography.Title>
						<Space wrap>
							<Tag color={statusColor(viewingVersion.status)}>
								{viewingVersion.status}
							</Tag>
							<Tag>{viewingVersion.versionOriginType}</Tag>
							{!isViewingLatest && (
								<Tag color="orange">
									最新版本为 v{document.latestVersionNumber}
								</Tag>
							)}
							{document.askableVersionNumber !== document.latestVersionNumber && (
								<Tag color="green">
									问答仍使用 v{document.askableVersionNumber}
								</Tag>
							)}
						</Space>
						<Typography.Paragraph>{viewingVersion.summary}</Typography.Paragraph>
						<Descriptions column={2} size="small">
							<Descriptions.Item label="documentId">
								{document.documentId}
							</Descriptions.Item>
							<Descriptions.Item label="knowledge base">
								{document.kbId}
							</Descriptions.Item>
							<Descriptions.Item label="文件名">
								{viewingVersion.filename}
							</Descriptions.Item>
							<Descriptions.Item label="文件大小">
								{formatFileSize(viewingVersion.fileSize)}
							</Descriptions.Item>
							<Descriptions.Item label="更新时间">
								{formatTime(viewingVersion.updatedAt)}
							</Descriptions.Item>
							<Descriptions.Item label="更新人">
								{viewingVersion.createdByDisplayName}
							</Descriptions.Item>
						</Descriptions>
						<div className="detail-page__actions">
							{!isViewingLatest && (
								<Button type="primary" onClick={backToLatest}>
									返回最新版本
								</Button>
							)}
							<Button onClick={() => openReadPage(viewingVersion.versionNumber)}>
								查看该版本内容
							</Button>
							{viewingVersion.canRollback && <Button>回退为最新版本</Button>}
						</div>
					</Card>

					<Card className="detail-page__panel">
						<Typography.Title level={5}>文档详情</Typography.Title>
						<Typography.Paragraph>
							这里承接稳定的文档资产信息。后续正式实现时，上传新版本、权限提示和删除入口都会落在这一主视图区域，而不是塞到列表页里。
						</Typography.Paragraph>
					</Card>

					<Card className="detail-page__panel">
						<Typography.Title level={5}>处理与问答上下文</Typography.Title>
						<Typography.Paragraph>
							当前最新版本状态为 {document.latestStatus}。问答基线仍落在 v
							{document.askableVersionNumber}，因此详情页要持续提示“治理主视图”和“问答实际使用版本”不是一回事。
						</Typography.Paragraph>
					</Card>
				</div>

				<Card className="detail-page__rail">
					<Typography.Text type="secondary">版本账本</Typography.Text>
					<Typography.Title level={4}>Version ledger</Typography.Title>
					<div className="detail-page__rail-scroll">
						<Space direction="vertical" size={12} style={{ width: "100%" }}>
							{document.versions.map((version) => {
								const isActive =
									version.versionNumber === viewingVersion.versionNumber;
								return (
									<button
										key={version.versionNumber}
										type="button"
										className={`detail-page__version-card ${
											isActive ? "is-active" : ""
										}`}
										onClick={() => {
											navigate(
												`/ingest/documents/${encodeURIComponent(document.documentId)}?version=${version.versionNumber}`,
											);
										}}
									>
										<div className="detail-page__version-card-top">
											<span>v{version.versionNumber}</span>
											<Tag color={statusColor(version.status)}>
												{version.status}
											</Tag>
										</div>
										<div className="detail-page__version-card-tags">
											{version.isLatestVersion && (
												<Tag color="gold">最新版本</Tag>
											)}
											{isActive && <Tag color="blue">当前查看</Tag>}
											{version.isAskableVersion && (
												<Tag color="green">当前问答基线</Tag>
											)}
											{version.versionOriginType === "ROLLBACK" && (
												<Tag color="orange">回退产生</Tag>
											)}
											{version.hasBeenRolledBackAsLatest && (
												<Tag>曾回退为最新版本</Tag>
											)}
										</div>
										<Typography.Text strong>
											{version.filename}
										</Typography.Text>
										<Typography.Paragraph type="secondary">
											{formatTime(version.updatedAt)} ·{" "}
											{version.createdByDisplayName}
										</Typography.Paragraph>
										<Typography.Paragraph>{version.note}</Typography.Paragraph>
										<div className="detail-page__version-card-actions">
											<Button
												size="small"
												type="link"
												onClick={(event) => {
													event.stopPropagation();
													openReadPage(version.versionNumber);
												}}
											>
												查看该版本内容
											</Button>
											{version.canRollback && (
												<Button size="small" type="link">
													回退为最新版本
												</Button>
											)}
										</div>
									</button>
								);
							})}
						</Space>
					</div>
				</Card>
			</div>
		</div>
	);
}
