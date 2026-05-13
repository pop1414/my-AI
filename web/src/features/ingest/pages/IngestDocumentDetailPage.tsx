import {
	ArrowLeftOutlined,
	EyeOutlined,
	HistoryOutlined,
	ReadOutlined,
	ReloadOutlined,
} from "@ant-design/icons";
import {
	Alert,
	Button,
	Card,
	Descriptions,
	Empty,
	Result,
	Skeleton,
	Space,
	Tag,
	Typography,
} from "antd";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
	getDocumentVersionHistory,
	type DocumentVersionHistoryItem,
} from "../../../shared/api/ingestApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import "./IngestDocumentDetailPage.css";

const defaultVisibleVersionCount = 5;

function formatTime(iso: string): string {
	return new Date(iso).toLocaleString("zh-CN", {
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
	});
}

function formatFileSize(fileSize: number): string {
	if (fileSize >= 1024 * 1024) {
		return `${(fileSize / (1024 * 1024)).toFixed(1)} MB`;
	}
	if (fileSize >= 1024) {
		return `${(fileSize / 1024).toFixed(0)} KB`;
	}
	return `${fileSize} B`;
}

function statusColor(status: string): string {
	switch (status) {
		case "UPLOADED":
			return "blue";
		case "INGESTING":
		case "PROCESSING":
			return "processing";
		case "INDEXED":
			return "success";
		case "FAILED":
			return "error";
		case "DELETING":
			return "warning";
		case "DELETED":
			return "default";
		default:
			return "default";
	}
}

function originLabel(originType: string): string {
	switch (originType) {
		case "UPLOAD":
			return "上传产生";
		case "ROLLBACK":
			return "回退产生";
		default:
			return originType;
	}
}

function getUploader(version: DocumentVersionHistoryItem): string {
	return (
		version.createdByDisplayName ??
		version.createdByUserId ??
		"上传人未记录"
	);
}

function buildDetailPath(documentId: string, versionNumber?: number): string {
	const base = `/ingest/documents/${encodeURIComponent(documentId)}`;
	return versionNumber ? `${base}?version=${versionNumber}` : base;
}

function buildReadPath(documentId: string, versionNumber: number): string {
	return `/ingest/documents/${encodeURIComponent(documentId)}/versions/${versionNumber}/read`;
}

function resolveVisibleVersions(
	versions: DocumentVersionHistoryItem[],
	viewingVersion: DocumentVersionHistoryItem,
	expanded: boolean,
): DocumentVersionHistoryItem[] {
	if (expanded || versions.length <= defaultVisibleVersionCount) {
		return versions;
	}

	const mustShow = new Set<number>([
		viewingVersion.versionNumber,
		...versions
			.filter((version) => version.isLatestVersion || version.isAskableVersion)
			.map((version) => version.versionNumber),
	]);
	const defaultVisible = versions
		.slice(0, defaultVisibleVersionCount)
		.map((version) => version.versionNumber);
	const visibleNumbers = new Set([...defaultVisible, ...mustShow]);
	return versions.filter((version) => visibleNumbers.has(version.versionNumber));
}

function DetailDiffSummary({
	viewingVersion,
	compareVersion,
	askableVersion,
}: {
	viewingVersion: DocumentVersionHistoryItem;
	compareVersion?: DocumentVersionHistoryItem;
	askableVersion?: DocumentVersionHistoryItem;
}) {
	if (!compareVersion) {
		return (
			<Alert
				type="info"
				showIcon
				message="当前为首个版本，暂无历史差异可比"
			/>
		);
	}

	const fileChanged =
		viewingVersion.filename !== compareVersion.filename ||
		viewingVersion.fileSize !== compareVersion.fileSize;

	return (
		<section className="detail-page__diff" data-testid="diff-summary">
			<div className="detail-page__section-title">
				<Typography.Title level={4}>差异摘要</Typography.Title>
				<Typography.Text type="secondary">
					只比较版本元数据，不做正文 diff。
				</Typography.Text>
			</div>
			<div className="detail-page__diff-grid">
				<Card size="small" className="detail-page__diff-card">
					<Typography.Text type="secondary">版本关系</Typography.Text>
					<Typography.Title level={5}>
						v{viewingVersion.versionNumber} vs v
						{compareVersion.versionNumber}
					</Typography.Title>
					<Typography.Paragraph type="secondary">
						{viewingVersion.isLatestVersion
							? "当前最新版本与上一版本对比。"
							: "当前历史版本与系统最新版本对比。"}
					</Typography.Paragraph>
				</Card>
				<Card size="small" className="detail-page__diff-card">
					<Typography.Text type="secondary">文件变化</Typography.Text>
					<Typography.Title level={5}>
						{fileChanged ? "文件事实有变化" : "文件事实一致"}
					</Typography.Title>
					<Typography.Paragraph type="secondary">
						{viewingVersion.filename} · {formatFileSize(viewingVersion.fileSize)}
					</Typography.Paragraph>
				</Card>
				<Card size="small" className="detail-page__diff-card">
					<Typography.Text type="secondary">处理与问答</Typography.Text>
					<Typography.Title level={5}>
						问答基线{" "}
						{askableVersion ? `v${askableVersion.versionNumber}` : "暂无"}
					</Typography.Title>
					<Typography.Paragraph type="secondary">
						当前查看版本状态为 {viewingVersion.status}，对比版本状态为{" "}
						{compareVersion.status}。
					</Typography.Paragraph>
				</Card>
				<Card size="small" className="detail-page__diff-card">
					<Typography.Text type="secondary">时间与来源</Typography.Text>
					<Typography.Title level={5}>
						{originLabel(viewingVersion.versionOriginType)}
					</Typography.Title>
					<Typography.Paragraph type="secondary">
						更新于 {formatTime(viewingVersion.updatedAt)}
					</Typography.Paragraph>
				</Card>
			</div>
		</section>
	);
}

function VersionTags({
	version,
	isActive,
}: {
	version: DocumentVersionHistoryItem;
	isActive: boolean;
}) {
	return (
		<Space size={[6, 6]} wrap>
			{version.isLatestVersion && <Tag color="gold">最新版本</Tag>}
			{isActive && <Tag color="blue">当前查看</Tag>}
			{version.isAskableVersion && <Tag color="green">当前问答基线</Tag>}
			{version.versionOriginType === "ROLLBACK" && (
				<Tag color="orange">回退产生</Tag>
			)}
			{version.hasBeenRolledBackAsLatest && <Tag>曾回退为最新版本</Tag>}
			<Tag color={statusColor(version.status)}>{version.status}</Tag>
		</Space>
	);
}

export function IngestDocumentDetailPage() {
	const navigate = useNavigate();
	const { documentId = "" } = useParams<{ documentId?: string }>();
	const [searchParams] = useSearchParams();
	const [expandedHistory, setExpandedHistory] = useState(false);

	const historyQuery = useQuery({
		queryKey: ["document-version-history", documentId],
		queryFn: () => getDocumentVersionHistory(documentId),
		enabled: documentId.length > 0,
	});

	const historyData = historyQuery.data;
	const versions = useMemo(() => historyData?.versions ?? [], [historyData]);
	const latestVersion =
		versions.find((version) => version.isLatestVersion) ?? versions[0];
	const askableVersion = versions.find((version) => version.isAskableVersion);
	const requestedVersionNumber = Number(searchParams.get("version"));
	const viewingVersion =
		versions.find(
			(version) => version.versionNumber === requestedVersionNumber,
		) ?? latestVersion;
	const isViewingLatest = Boolean(viewingVersion?.isLatestVersion);
	const compareVersion = useMemo(() => {
		if (!viewingVersion) {
			return undefined;
		}
		if (!isViewingLatest) {
			return latestVersion;
		}
		return versions.find(
			(version) => version.versionNumber < viewingVersion.versionNumber,
		);
	}, [isViewingLatest, latestVersion, versions, viewingVersion]);
	const visibleVersions = useMemo(
		() =>
			viewingVersion
				? resolveVisibleVersions(versions, viewingVersion, expandedHistory)
				: [],
		[expandedHistory, versions, viewingVersion],
	);
	const hiddenVersionCount = versions.length - visibleVersions.length;
	const errorStatus = (historyQuery.error as { status?: number } | null)?.status;

	if (historyQuery.isLoading) {
		return (
			<div className="detail-page">
				<Card className="detail-page__panel">
					<Skeleton active paragraph={{ rows: 6 }} />
				</Card>
			</div>
		);
	}

	if (historyQuery.isError && errorStatus === 403) {
		return (
			<Result
				status="403"
				title="旧版本视图不可见"
				subTitle="版本历史仅对具备目标 document 管理权限的用户开放。当前用户可以继续从文档列表进入自己有权限的文档。"
				extra={
					<Button onClick={() => navigate("/ingest/documents")}>
						返回文档列表
					</Button>
				}
			/>
		);
	}

	if (historyQuery.isError) {
		return (
			<div className="detail-page">
				<ApiErrorAlert error={historyQuery.error} />
			</div>
		);
	}

	if (!viewingVersion || !latestVersion) {
		return (
			<Card className="detail-page__panel">
				<Empty description="暂无版本历史" />
			</Card>
		);
	}

	return (
		<div className="detail-page">
			<div className="detail-page__header">
				<div>
					<Typography.Text className="detail-page__kicker">
						document version ledger
					</Typography.Text>
					<Typography.Title level={2}>文档详情</Typography.Title>
					<Typography.Paragraph type="secondary">
						{historyData?.documentId}
					</Typography.Paragraph>
				</div>
				<Space wrap>
					<Button
						icon={<ArrowLeftOutlined />}
						onClick={() => navigate("/ingest/documents")}
					>
						返回文档列表
					</Button>
					<Button
						icon={<ReloadOutlined />}
						loading={historyQuery.isFetching}
						onClick={() => historyQuery.refetch()}
					>
						刷新
					</Button>
					<Button
						type="primary"
						icon={<ReadOutlined />}
						onClick={() =>
							navigate(
								buildReadPath(
									historyData!.documentId,
									viewingVersion.versionNumber,
								),
							)
						}
					>
						查看该版本内容
					</Button>
				</Space>
			</div>

			{!isViewingLatest && (
				<Alert
					data-testid="history-alert"
					type="warning"
					showIcon
					message={`正在查看历史版本 v${viewingVersion.versionNumber}`}
					description="此视图仅用于审计与治理查看，不会改变当前最新版本与问答基线。"
					action={
						<Button
							size="small"
							type="primary"
							data-testid="return-latest"
							onClick={() =>
								navigate(buildDetailPath(historyData!.documentId))
							}
						>
							返回最新版本
						</Button>
					}
				/>
			)}

			<DetailDiffSummary
				viewingVersion={viewingVersion}
				compareVersion={compareVersion}
				askableVersion={askableVersion}
			/>

			<div className="detail-page__grid">
				<div className="detail-page__main">
					<Card className="detail-page__panel detail-page__panel--accent">
						<div className="detail-page__section-title">
							<div>
								<Typography.Text type="secondary">
									{isViewingLatest ? "最新版本概览" : "历史版本概览"}
								</Typography.Text>
								<Typography.Title level={3}>
									v{viewingVersion.versionNumber}
								</Typography.Title>
							</div>
							<VersionTags version={viewingVersion} isActive />
						</div>

						<Descriptions column={{ xs: 1, sm: 2 }} size="small">
							<Descriptions.Item label="documentId">
								{viewingVersion.documentId}
							</Descriptions.Item>
							<Descriptions.Item label="系统最新版本">
								v{latestVersion.versionNumber}
							</Descriptions.Item>
							<Descriptions.Item label="当前问答基线">
								{askableVersion
									? `v${askableVersion.versionNumber}`
									: "暂无可问答版本"}
							</Descriptions.Item>
							<Descriptions.Item label="版本来源">
								{originLabel(viewingVersion.versionOriginType)}
							</Descriptions.Item>
							{viewingVersion.rollbackFromVersionNumber && (
								<Descriptions.Item label="回退来源">
									v{viewingVersion.rollbackFromVersionNumber}
								</Descriptions.Item>
							)}
							<Descriptions.Item label="文件名">
								{viewingVersion.filename}
							</Descriptions.Item>
							<Descriptions.Item label="文件大小">
								{formatFileSize(viewingVersion.fileSize)}
							</Descriptions.Item>
							<Descriptions.Item label="上传人">
								{getUploader(viewingVersion)}
							</Descriptions.Item>
							<Descriptions.Item label="上传时间">
								{formatTime(viewingVersion.createdAt)}
							</Descriptions.Item>
							<Descriptions.Item label="更新时间">
								{formatTime(viewingVersion.updatedAt)}
							</Descriptions.Item>
						</Descriptions>

						<div className="detail-page__actions">
							{!isViewingLatest && (
								<Button
									type="primary"
									onClick={() =>
										navigate(buildDetailPath(historyData!.documentId))
									}
								>
									返回最新版本
								</Button>
							)}
							<Button
								icon={<ReadOutlined />}
								onClick={() =>
									navigate(
										buildReadPath(
											historyData!.documentId,
											viewingVersion.versionNumber,
										),
									)
								}
							>
								查看该版本内容
							</Button>
						</div>
					</Card>

					<Card className="detail-page__panel">
						<Typography.Title level={4}>处理与问答上下文</Typography.Title>
						<Space direction="vertical" size={10}>
							<Typography.Text>
								当前查看版本状态：
								<Tag color={statusColor(viewingVersion.status)}>
									{viewingVersion.status}
								</Tag>
							</Typography.Text>
							<Typography.Text>
								问答基线：
								{askableVersion ? (
									<Tag color="green">v{askableVersion.versionNumber}</Tag>
								) : (
									<Tag>暂无可问答版本</Tag>
								)}
							</Typography.Text>
							{latestVersion.versionNumber !== askableVersion?.versionNumber && (
								<Alert
									type="warning"
									showIcon
									message={`当前问答仍使用 ${
										askableVersion
											? `v${askableVersion.versionNumber}`
											: "上一可用版本"
									}，等待最新版本可用后才会切换`}
								/>
							)}
							{viewingVersion.failureReason && (
								<Alert
									type="error"
									showIcon
									message="处理失败原因"
									description={viewingVersion.failureReason}
								/>
							)}
						</Space>
					</Card>
				</div>

				<Card className="detail-page__rail" data-testid="version-history-list">
					<div className="detail-page__section-title">
						<div>
							<Typography.Text type="secondary">版本历史</Typography.Text>
							<Typography.Title level={4}>
								<HistoryOutlined /> 版本账本
							</Typography.Title>
						</div>
						<Tag>{historyData?.sort}</Tag>
					</div>
					<div className="detail-page__rail-scroll">
						<Space direction="vertical" size={10} style={{ width: "100%" }}>
							{visibleVersions.map((version) => {
								const isActive =
									version.versionNumber === viewingVersion.versionNumber;
								return (
									<div
										key={version.versionNumber}
										className={`detail-page__version-card ${
											isActive ? "is-active" : ""
										}`}
										data-testid={`version-card-${version.versionNumber}`}
									>
										<button
											type="button"
											className="detail-page__version-card-button"
											onClick={() =>
												navigate(
													buildDetailPath(
														historyData!.documentId,
														version.isLatestVersion
															? undefined
															: version.versionNumber,
													),
												)
											}
										>
											<span className="detail-page__version-number">
												v{version.versionNumber}
											</span>
											<VersionTags version={version} isActive={isActive} />
											<span className="detail-page__filename">
												{version.filename}
											</span>
											<span className="detail-page__version-meta">
												{getUploader(version)} · {formatTime(version.updatedAt)}
											</span>
											{version.rollbackFromVersionNumber && (
												<span className="detail-page__version-note">
													回退自 v{version.rollbackFromVersionNumber}
												</span>
											)}
										</button>
										<div className="detail-page__version-actions">
											<Button
												size="small"
												type="text"
												icon={<EyeOutlined />}
												onClick={() =>
													navigate(
														buildDetailPath(
														historyData!.documentId,
															version.isLatestVersion
																? undefined
																: version.versionNumber,
														),
													)
												}
											>
												查看详情
											</Button>
											<Button
												size="small"
												type="text"
												icon={<ReadOutlined />}
												onClick={() =>
													navigate(
														buildReadPath(
														historyData!.documentId,
															version.versionNumber,
														),
													)
												}
											>
												查看该版本内容
											</Button>
										</div>
									</div>
								);
							})}
						</Space>
					</div>
					{hiddenVersionCount > 0 && (
						<Button
							block
							onClick={() => setExpandedHistory(true)}
						>
							展开更早版本（{hiddenVersionCount}）
						</Button>
					)}
				</Card>
			</div>
		</div>
	);
}
