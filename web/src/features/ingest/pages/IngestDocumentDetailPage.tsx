import {
	ArrowLeftOutlined,
	DeleteOutlined,
	EyeOutlined,
	HistoryOutlined,
	InboxOutlined,
	ReadOutlined,
	ReloadOutlined,
	RollbackOutlined,
	UploadOutlined,
} from "@ant-design/icons";
import {
	Alert,
	Button,
	Card,
	Descriptions,
	Empty,
	Modal,
	Result,
	Skeleton,
	Space,
	Tag,
	Typography,
	Upload,
} from "antd";
import type { UploadFile } from "antd/es/upload/interface";
import { type ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	Link,
	type To,
	useNavigate,
	useParams,
	useSearchParams,
} from "react-router-dom";
import {
	deleteDocument,
	getDocumentVersionHistory,
	rollbackDocumentVersion,
	uploadNewDocumentVersion,
	type DocumentVersionRollbackResponse,
	type DocumentVersionUploadResponse,
	type DocumentVersionHistoryItem,
} from "../../../shared/api/ingestApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { DeleteDocumentConfirmModal } from "./DeleteDocumentConfirmModal";
import "./IngestDocumentDetailPage.css";

const defaultVisibleVersionCount = 5;
const uploadAllowedStatuses = new Set(["INDEXED", "FAILED"]);
const rollbackAllowedLatestStatuses = new Set(["INDEXED", "FAILED"]);
const { Dragger } = Upload;
const fileSizeFormatter = new Intl.NumberFormat("zh-CN", {
	maximumFractionDigits: 1,
});

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
		return `${fileSizeFormatter.format(fileSize / (1024 * 1024))}\u00a0MB`;
	}
	if (fileSize >= 1024) {
		return `${fileSizeFormatter.format(fileSize / 1024)}\u00a0KB`;
	}
	return `${fileSizeFormatter.format(fileSize)}\u00a0B`;
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

function resolveHasBeenRolledBackAsLatest(
	version: DocumentVersionHistoryItem,
	versions: DocumentVersionHistoryItem[],
): boolean {
	return Boolean(
		version.hasBeenRolledBackAsLatest ??
			versions.some(
				(item) =>
					item.versionOriginType === "ROLLBACK" &&
					item.rollbackFromVersionNumber === version.versionNumber,
			),
	);
}

function canRollbackVersion(
	version: DocumentVersionHistoryItem,
	latestVersion: DocumentVersionHistoryItem,
): boolean {
	return Boolean(
		version.canRollback ??
			(!version.isLatestVersion &&
				version.status === "INDEXED" &&
				rollbackAllowedLatestStatuses.has(latestVersion.status)),
	);
}

function buildDetailPath(
	documentId: string,
	versionNumber?: number,
	returnTo?: string,
): string {
	const base = `/ingest/documents/${encodeURIComponent(documentId)}`;
	const params = new URLSearchParams();
	if (versionNumber) params.set("version", String(versionNumber));
	if (returnTo) params.set("returnTo", returnTo);
	const qs = params.toString();
	return `${base}${qs ? `?${qs}` : ""}`;
}

function resolveListReturnTo(searchParams: URLSearchParams): string {
	const returnTo = searchParams.get("returnTo");
	if (returnTo?.startsWith("/ingest/documents")) {
		return returnTo;
	}
	return "/ingest/documents";
}

function buildDeletedListPath(returnTo: string, documentId: string): string {
	const url = new URL(returnTo, "http://my-ai.local");
	url.searchParams.set("deletedDocumentId", documentId);
	return `${url.pathname}${url.search}`;
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
				<Typography.Title level={3}>差异摘要</Typography.Title>
				<Typography.Text type="secondary">
					只比较版本元数据，不做正文 diff。
				</Typography.Text>
			</div>
			<div className="detail-page__diff-grid">
				<Card size="small" className="detail-page__diff-card">
					<Typography.Text type="secondary">版本关系</Typography.Text>
					<Typography.Title level={4}>
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
					<Typography.Title level={4}>
						{fileChanged ? "文件事实有变化" : "文件事实一致"}
					</Typography.Title>
					<Typography.Paragraph type="secondary">
						{viewingVersion.filename} · {formatFileSize(viewingVersion.fileSize)}
					</Typography.Paragraph>
				</Card>
				<Card size="small" className="detail-page__diff-card">
					<Typography.Text type="secondary">处理与问答</Typography.Text>
					<Typography.Title level={4}>
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
					<Typography.Title level={4}>
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
	hasBeenRolledBackAsLatest,
}: {
	version: DocumentVersionHistoryItem;
	isActive: boolean;
	hasBeenRolledBackAsLatest: boolean;
}) {
	return (
		<Space size={[6, 6]} wrap>
			{version.isLatestVersion && <Tag color="gold">最新版本</Tag>}
			{isActive && <Tag color="blue">当前查看</Tag>}
			{version.isAskableVersion && <Tag color="green">当前问答基线</Tag>}
			{version.versionOriginType === "ROLLBACK" && (
				<Tag color="orange">回退产生</Tag>
			)}
			{hasBeenRolledBackAsLatest && <Tag>曾回退为最新版本</Tag>}
			<Tag color={statusColor(version.status)}>{version.status}</Tag>
		</Space>
	);
}

function VersionUploadResultAlert({
	result,
	onClose,
	onShowHistory,
}: {
	result: DocumentVersionUploadResponse;
	onClose: () => void;
	onShowHistory: () => void;
}) {
	const visibleVersionNumber =
		result.versionNumber ??
		result.reusedLatestVersionNumber ??
		result.latestVersionNumber;
	const title = result.versionCreated
		? `已创建新版本 v${visibleVersionNumber}`
		: "未创建新版本";
	const description = result.versionCreated
		? `上一版本为 v${result.previousVersionNumber}，当前详情页已切换到最新版本。`
		: `上传文件与当前最新版本内容一致，当前仍停留在 v${visibleVersionNumber}。`;

	return (
		<Alert
			className="detail-page__result-alert"
			data-result-kind={result.versionCreated ? "success" : "info"}
			data-testid="version-upload-result"
			aria-live="polite"
			aria-atomic="true"
			type={result.versionCreated ? "success" : "info"}
			showIcon
			message={title}
			description={
				<div className="detail-page__result-body">
					<Typography.Paragraph>{description}</Typography.Paragraph>
					<div className="detail-page__result-facts">
						<span>documentId：{result.documentId}</span>
						<span>latestVersionNumber：v{result.latestVersionNumber}</span>
						<span>
							previousVersionNumber：
							{result.previousVersionNumber
								? `v${result.previousVersionNumber}`
								: "-"}
						</span>
						<span>status：{result.status}</span>
						<span>
							askableVersionNumber：
							{result.askableVersionNumber
								? `v${result.askableVersionNumber}`
								: "暂无"}
						</span>
					</div>
					{!result.canAskNow && (
						<Typography.Text type="secondary">
							当前暂无可问答版本，请等待处理完成。
						</Typography.Text>
					)}
				</div>
			}
			action={
				<Space wrap>
					<Button size="small" onClick={onShowHistory}>
						查看版本历史
					</Button>
					{result.canAskNow && (
						<RouterButtonLink size="small" tone="primary" to="/qa">
							去问答
						</RouterButtonLink>
					)}
					<Button size="small" type="text" onClick={onClose}>
						关闭提示
					</Button>
				</Space>
			}
		/>
	);
}

function VersionRollbackResultAlert({
	result,
	onClose,
	onShowHistory,
}: {
	result: DocumentVersionRollbackResponse;
	onClose: () => void;
	onShowHistory: () => void;
}) {
	const latestIndexed = result.status === "INDEXED";
	const askableText = result.askableVersionNumber
		? `v${result.askableVersionNumber}`
		: "暂无可问答版本";

	return (
		<Alert
			className="detail-page__result-alert"
			data-result-kind="success"
			data-testid="version-rollback-result"
			aria-live="polite"
			aria-atomic="true"
			type="success"
			showIcon
			message={`已回退为新的最新版本 v${result.latestVersionNumber}`}
			description={
				<div className="detail-page__result-body">
					<Typography.Paragraph>
						已基于历史版本 v{result.rollbackFromVersionNumber} 创建回退版本
						v{result.versionNumber}，页面已切换到新的最新版本。
					</Typography.Paragraph>
					<div className="detail-page__result-facts">
						<span>documentId：{result.documentId}</span>
						<span>latestVersionNumber：v{result.latestVersionNumber}</span>
						<span>
							rollbackFromVersionNumber：v{result.rollbackFromVersionNumber}
						</span>
						<span>status：{result.status}</span>
						<span>askableVersionNumber：{askableText}</span>
					</div>
					{!latestIndexed && (
						<Alert
							type="warning"
							showIcon
							message={`新最新版本 v${result.latestVersionNumber} 尚未 INDEXED`}
							description={`当前问答暂时仍使用最近一个已 INDEXED 的版本：${askableText}。`}
						/>
					)}
				</div>
			}
			action={
				<Space wrap>
					<Button size="small" onClick={onShowHistory}>
						查看版本历史
					</Button>
					{result.canAskNow && (
						<RouterButtonLink size="small" tone="primary" to="/qa">
							去问答
						</RouterButtonLink>
					)}
					<Button size="small" type="text" onClick={onClose}>
						关闭提示
					</Button>
				</Space>
			}
		/>
	);
}

function ReadUnavailableNotice({ size }: { size?: "small" }) {
	const className = [
		"detail-page__read-unavailable",
		size ? "detail-page__read-unavailable--small" : "",
	]
		.filter(Boolean)
		.join(" ");

	return (
		<span
			aria-label="正文阅读接口尚未接入，暂不支持阅读跳转"
			className={className}
		>
			<ReadOutlined />
			正文阅读待接入
		</span>
	);
}

function RouterButtonLink({
	children,
	icon,
	to,
	tone = "default",
	size,
	block,
	testId,
}: {
	children: ReactNode;
	icon?: ReactNode;
	to: To;
	tone?: "default" | "primary" | "text";
	size?: "small";
	block?: boolean;
	testId?: string;
}) {
	const className = [
		"detail-page__button-link",
		`detail-page__button-link--${tone}`,
		size ? `detail-page__button-link--${size}` : "",
		block ? "detail-page__button-link--block" : "",
	]
		.filter(Boolean)
		.join(" ");

	return (
		<Link className={className} data-testid={testId} to={to}>
			{icon}
			<span>{children}</span>
		</Link>
	);
}

export function IngestDocumentDetailPage() {
	const { documentId = "" } = useParams<{ documentId?: string }>();
	const [searchParams, setSearchParams] = useSearchParams();
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const [uploadModalOpen, setUploadModalOpen] = useState(false);
	const [deleteModalOpen, setDeleteModalOpen] = useState(false);
	const [uploadFileList, setUploadFileList] = useState<UploadFile[]>([]);
	const [uploadResult, setUploadResult] =
		useState<DocumentVersionUploadResponse | null>(null);
	const [rollbackTarget, setRollbackTarget] =
		useState<DocumentVersionHistoryItem | null>(null);
	const [rollbackResult, setRollbackResult] =
		useState<DocumentVersionRollbackResponse | null>(null);
	const expandedHistory = searchParams.get("history") === "expanded";
	const listReturnTo = useMemo(
		() => resolveListReturnTo(searchParams),
		[searchParams],
	);
	const preservedReturnTo = searchParams.get("returnTo") ?? undefined;

	const historyQuery = useQuery({
		queryKey: ["document-version-history", documentId],
		queryFn: () => getDocumentVersionHistory(documentId),
		enabled: documentId.length > 0,
	});

	const historyData = historyQuery.data;
	const versions = useMemo(
		() =>
			(historyData?.versions ?? []).map((version) => ({
				...version,
				hasBeenRolledBackAsLatest: resolveHasBeenRolledBackAsLatest(
					version,
					historyData?.versions ?? [],
				),
			})),
		[historyData],
	);
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
	const canUploadNewVersion = Boolean(
		isViewingLatest &&
			latestVersion &&
			uploadAllowedStatuses.has(latestVersion.status),
	);
	const uploadVersionMutation = useMutation({
		mutationFn: (file: File) =>
			uploadNewDocumentVersion({
				documentId,
				file,
				expectedLatestVersionNumber: latestVersion!.versionNumber,
			}),
		onSuccess: async (data) => {
			setUploadResult(data);
			setUploadModalOpen(false);
			setUploadFileList([]);
			setSearchParams((current) => {
				const next = new URLSearchParams(current);
				next.delete("version");
				return next;
			});
			await queryClient.invalidateQueries({
				queryKey: ["document-version-history", documentId],
			});
		},
	});
	const rollbackVersionMutation = useMutation({
		mutationFn: (targetVersionNumber: number) =>
			rollbackDocumentVersion({
				documentId,
				targetVersionNumber,
				expectedLatestVersionNumber: latestVersion!.versionNumber,
			}),
		onSuccess: async (data) => {
			setRollbackResult(data);
			setRollbackTarget(null);
			setSearchParams((current) => {
				const next = new URLSearchParams(current);
				next.delete("version");
				return next;
			});
			await queryClient.invalidateQueries({
				queryKey: ["document-version-history", documentId],
			});
		},
	});
	const deleteDocumentMutation = useMutation({
		mutationFn: () => deleteDocument(documentId),
		onSuccess: async () => {
			await Promise.all([
				queryClient.invalidateQueries({ queryKey: ["documents"] }),
				queryClient.invalidateQueries({
					queryKey: ["document-version-history", documentId],
				}),
			]);
			navigate(buildDeletedListPath(listReturnTo, documentId), {
				replace: true,
			});
		},
	});
	const expandHistory = () => {
		setSearchParams((current) => {
			const next = new URLSearchParams(current);
			next.set("history", "expanded");
			return next;
		});
	};
	const submitNewVersion = async () => {
		const selectedFile = uploadFileList[0]?.originFileObj;
		if (!selectedFile || !latestVersion) {
			return;
		}

		await uploadVersionMutation.mutateAsync(selectedFile);
	};
	const submitRollbackVersion = async () => {
		if (!rollbackTarget) {
			return;
		}

		await rollbackVersionMutation.mutateAsync(rollbackTarget.versionNumber);
	};

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
					<RouterButtonLink to="/ingest/documents">
						返回文档列表
					</RouterButtonLink>
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
				<div className="detail-page__header-copy">
					<Typography.Text className="detail-page__kicker">
						document version ledger
					</Typography.Text>
					<Typography.Title level={2}>文档详情</Typography.Title>
					<Typography.Paragraph
						className="detail-page__document-id"
						type="secondary"
						copyable={
							historyData?.documentId ? { text: historyData.documentId } : false
						}
						ellipsis={{ rows: 2, expandable: true, symbol: "展开" }}
					>
						{historyData?.documentId}
					</Typography.Paragraph>
				</div>
				<Space className="detail-page__header-actions" wrap>
					<RouterButtonLink
						icon={<ArrowLeftOutlined />}
						to={listReturnTo}
					>
						返回文档列表
					</RouterButtonLink>
					<Button
						icon={<ReloadOutlined />}
						loading={historyQuery.isFetching}
						onClick={() => historyQuery.refetch()}
					>
						刷新
					</Button>
					{canUploadNewVersion && (
						<Button
							type="primary"
							icon={<UploadOutlined />}
							onClick={() => setUploadModalOpen(true)}
						>
							上传新版本
						</Button>
					)}
					{latestVersion.status !== "DELETED" &&
						latestVersion.status !== "DELETING" && (
							<Button
								danger
								icon={<DeleteOutlined />}
								onClick={() => setDeleteModalOpen(true)}
							>
								删除 document
							</Button>
						)}
					<ReadUnavailableNotice />
				</Space>
			</div>

			{uploadResult && (
				<VersionUploadResultAlert
					result={uploadResult}
					onClose={() => setUploadResult(null)}
					onShowHistory={expandHistory}
				/>
			)}
			{rollbackResult && (
				<VersionRollbackResultAlert
					result={rollbackResult}
					onClose={() => setRollbackResult(null)}
					onShowHistory={expandHistory}
				/>
			)}

			{!isViewingLatest && (
				<Alert
					data-testid="history-alert"
					type="warning"
					showIcon
					message={`正在查看历史版本 v${viewingVersion.versionNumber}`}
					description="此视图仅用于审计与治理查看，不会改变当前最新版本与问答基线。"
					action={
						<RouterButtonLink
							size="small"
							tone="primary"
							testId="return-latest"
							to={buildDetailPath(
								historyData!.documentId,
								undefined,
								preservedReturnTo,
							)}
						>
							返回最新版本
						</RouterButtonLink>
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
							<VersionTags
								version={viewingVersion}
								isActive
								hasBeenRolledBackAsLatest={resolveHasBeenRolledBackAsLatest(
									viewingVersion,
									versions,
								)}
							/>
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
							{canUploadNewVersion && (
								<Button
									icon={<UploadOutlined />}
									onClick={() => setUploadModalOpen(true)}
								>
									上传新版本
								</Button>
							)}
							{!isViewingLatest && (
								<RouterButtonLink
									tone="primary"
									to={buildDetailPath(
										historyData!.documentId,
										undefined,
										preservedReturnTo,
									)}
								>
									返回最新版本
								</RouterButtonLink>
							)}
							{!isViewingLatest &&
								canRollbackVersion(viewingVersion, latestVersion) && (
									<Button
										icon={<RollbackOutlined />}
										onClick={() => setRollbackTarget(viewingVersion)}
									>
										回退为最新版本
									</Button>
								)}
							<ReadUnavailableNotice />
						</div>
					</Card>

					<Card className="detail-page__panel">
						<Typography.Title level={3}>处理与问答上下文</Typography.Title>
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
							<Typography.Title level={3}>
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
										<Link
											className="detail-page__version-card-button"
											to={buildDetailPath(
												historyData!.documentId,
												version.isLatestVersion
													? undefined
													: version.versionNumber,
												preservedReturnTo,
											)}
										>
											<span className="detail-page__version-number">
												v{version.versionNumber}
											</span>
											<VersionTags
												version={version}
												isActive={isActive}
												hasBeenRolledBackAsLatest={resolveHasBeenRolledBackAsLatest(
													version,
													versions,
												)}
											/>
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
										</Link>
										<div className="detail-page__version-actions">
											<RouterButtonLink
												size="small"
												tone="text"
												icon={<EyeOutlined />}
												to={buildDetailPath(
													historyData!.documentId,
													version.isLatestVersion
														? undefined
														: version.versionNumber,
													preservedReturnTo,
												)}
											>
												查看详情
											</RouterButtonLink>
											<ReadUnavailableNotice size="small" />
											{canRollbackVersion(version, latestVersion) && (
												<Button
													size="small"
													type="text"
													icon={<RollbackOutlined />}
													onClick={() => setRollbackTarget(version)}
												>
													回退为最新版本
												</Button>
											)}
										</div>
									</div>
								);
							})}
						</Space>
					</div>
					{hiddenVersionCount > 0 && (
						<Button
							block
							onClick={expandHistory}
						>
							展开更早版本（{hiddenVersionCount}）
						</Button>
					)}
				</Card>
			</div>

			<Modal
				title={`上传新版本 · 当前最新 v${latestVersion.versionNumber}`}
				open={uploadModalOpen}
				okText="提交新版本"
				cancelText="取消"
				confirmLoading={uploadVersionMutation.isPending}
				okButtonProps={{ disabled: uploadFileList.length === 0 }}
				onOk={submitNewVersion}
				onCancel={() => {
					setUploadModalOpen(false);
					setUploadFileList([]);
				}}
			>
				<Space direction="vertical" size={12} style={{ width: "100%" }}>
					<Alert
						type="info"
						showIcon
						message="流程已锁定当前 document 所属 knowledge base"
						description="上传新版本只绑定当前 document，不提供 knowledge base 切换项。"
					/>
					<Dragger
						multiple={false}
						maxCount={1}
						fileList={uploadFileList}
						beforeUpload={() => false}
						onChange={(info) => setUploadFileList(info.fileList.slice(-1))}
						onRemove={() => setUploadFileList([])}
					>
						<p className="ant-upload-drag-icon">
							<InboxOutlined />
						</p>
						<p className="ant-upload-text">选择要作为新版本的文件</p>
						<p className="ant-upload-hint">
							提交时会携带 expectedLatestVersionNumber =
							{latestVersion.versionNumber}。
						</p>
					</Dragger>
					{uploadVersionMutation.isError && (
						<ApiErrorAlert error={uploadVersionMutation.error} />
					)}
				</Space>
			</Modal>
			<Modal
				title={
					rollbackTarget
						? `确认回退 v${rollbackTarget.versionNumber}`
						: "确认回退"
				}
				open={Boolean(rollbackTarget)}
				okText="确认回退为最新版本"
				cancelText="取消"
				confirmLoading={rollbackVersionMutation.isPending}
				onOk={submitRollbackVersion}
				onCancel={() => {
					setRollbackTarget(null);
				}}
			>
				<Space direction="vertical" size={12} style={{ width: "100%" }}>
					<Alert
						type="warning"
						showIcon
						message="该操作会创建新的最新版本，并可能改变问答基线"
						description={
							rollbackTarget
								? `系统会基于 v${rollbackTarget.versionNumber} 创建 ROLLBACK 来源的新版本，不会覆盖原历史记录。当前最新版本为 v${latestVersion.versionNumber}，提交时会携带 expectedLatestVersionNumber=${latestVersion.versionNumber}。`
								: undefined
						}
					/>
					{rollbackTarget && (
						<Descriptions column={1} size="small">
							<Descriptions.Item label="回退目标">
								v{rollbackTarget.versionNumber}
							</Descriptions.Item>
							<Descriptions.Item label="目标文件">
								{rollbackTarget.filename}
							</Descriptions.Item>
							<Descriptions.Item label="目标状态">
								<Tag color={statusColor(rollbackTarget.status)}>
									{rollbackTarget.status}
								</Tag>
							</Descriptions.Item>
						</Descriptions>
					)}
					{rollbackVersionMutation.isError && (
						<ApiErrorAlert error={rollbackVersionMutation.error} />
					)}
				</Space>
			</Modal>
			<DeleteDocumentConfirmModal
				open={deleteModalOpen}
				document={{
					documentId,
					filename: latestVersion.filename,
					status: latestVersion.status,
					latestVersionNumber: latestVersion.versionNumber,
					latestVersionOriginType: latestVersion.versionOriginType,
				}}
				confirmLoading={deleteDocumentMutation.isPending}
				error={deleteDocumentMutation.error}
				onCancel={() => setDeleteModalOpen(false)}
				onConfirm={() => deleteDocumentMutation.mutate()}
			/>
		</div>
	);
}
