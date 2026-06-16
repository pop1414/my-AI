import {
	ArrowLeftOutlined,
	DeleteOutlined,
	InboxOutlined,
	ReloadOutlined,
	RollbackOutlined,
	UploadOutlined,
  CopyOutlined,
  HistoryOutlined,
  BookOutlined,
  FolderOpenOutlined,
} from "@ant-design/icons";
import {
	Alert,
	Button,
	Modal,
	Result,
	Skeleton,
	Space,
	Upload,
  message,
} from "antd";
import type { UploadFile } from "antd/es/upload/interface";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
	deleteDocument,
	getDocumentVersionHistory,
  getDocumentStatus,
	rollbackDocumentVersion,
	uploadNewDocumentVersion,
	type DocumentVersionRollbackResponse,
	type DocumentVersionUploadResponse,
	type DocumentVersionHistoryItem,
} from "../../../shared/api/ingestApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { DeleteDocumentConfirmModal } from "./DeleteDocumentConfirmModal";
import { DetailDiffSummary } from "../components/DetailDiffSummary";
import { VersionTags } from "../components/VersionTags";
import { VersionUploadResultAlert } from "../components/VersionUploadResultAlert";
import { VersionRollbackResultAlert } from "../components/VersionRollbackResultAlert";
import { formatFileSize, formatTime } from "../utils/formatters";
import { VersionHistoryList } from "../components/VersionHistoryList";
import { useAuth } from "../../../shared/auth/AuthContext";
import "./IngestDocumentDetailPage.css";

const uploadAllowedStatuses = new Set(["INDEXED", "FAILED"]);
const rollbackAllowedLatestStatuses = new Set(["INDEXED", "FAILED"]);
const { Dragger } = Upload;

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

export function IngestDocumentDetailPage() {
	const { isAdmin, status } = useAuth();
	const navigate = useNavigate();
	const { documentId = "" } = useParams<{ documentId?: string }>();
	const [searchParams, setSearchParams] = useSearchParams();
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

	// 等待认证状态加载完成
	if (status === 'loading') {
		return <div className="detail-page"><Skeleton active paragraph={{ rows: 10 }} /></div>;
	}

	if (!isAdmin) {
		return <Navigate to={`/member/read/${encodeURIComponent(documentId)}`} replace />;
	}

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

  const statusQuery = useQuery({
    queryKey: ["document-status", documentId],
    queryFn: () => getDocumentStatus(documentId),
    enabled: documentId.length > 0,
  });

  const kbQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
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

	const errorStatus = (historyQuery.error as { status?: number } | null)?.status;
	const canUploadNewVersion = Boolean(
		isViewingLatest &&
			latestVersion &&
			uploadAllowedStatuses.has(latestVersion.status),
	);

  const kbId = statusQuery.data?.kbId;
  const knowledgeBase = useMemo(() => {
    return kbId ? kbQuery.data?.find(kb => kb.id === kbId) : undefined;
  }, [kbId, kbQuery.data]);
  const knowledgeBaseName = knowledgeBase?.name ?? kbId;
  const knowledgeBaseIdLabel =
    knowledgeBase && knowledgeBase.name !== knowledgeBase.id
      ? knowledgeBase.id
      : undefined;

	const uploadVersionMutation = useMutation({
		mutationFn: (file: File) =>
			uploadNewDocumentVersion({
				documentId,
				file,
				expectedLatestVersionNumber: latestVersion!.versionNumber,
			}),
		onSuccess: async (data) => {
			setUploadResult(data);
      setRollbackResult(null); // Clear rollback result
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
      setUploadResult(null); // Clear upload result
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
				<Skeleton active paragraph={{ rows: 10 }} />
			</div>
		);
	}

	if (historyQuery.isError && errorStatus === 403) {
		return (
			<Result
				status="403"
				title="权限不足"
				subTitle="该文档的版本账本仅对管理员或具备特定权限的用户可见。"
				extra={
					<Button type="primary" onClick={() => navigate(listReturnTo)}>
						返回文档目录
					</Button>
				}
			/>
		);
	}

	if (!viewingVersion || !latestVersion) {
		return (
			<div className="detail-page">
				<Result
					status="info"
					title="未找到版本历史"
					extra={
						<Button onClick={() => navigate(listReturnTo)}>返回目录</Button>
					}
				/>
			</div>
		);
	}

	return (
		<div className="detail-page">
			{/* --- Header Section --- */}
			<header className="detail-page__header">
				<div className="detail-page__header-copy">
					<span className="detail-page__kicker">document version ledger</span>
					<h1 className="detail-page__title">文档详情</h1>
					<Space wrap size="middle">
            <div className="detail-page__document-id-box">
						  <HistoryOutlined style={{ color: 'var(--detail-accent)' }} />
						  <span>{documentId}</span>
						  <CopyOutlined 
                style={{ cursor: 'pointer', marginLeft: 4 }} 
                onClick={() => {
                  navigator.clipboard.writeText(documentId);
                  message.success("ID 已复制");
                }} 
              />
					  </div>
            {knowledgeBaseName && (
              <div className="detail-page__document-id-box" style={{ background: 'transparent' }}>
                <FolderOpenOutlined style={{ color: 'var(--detail-ink-secondary)' }} />
                <span>
                  {knowledgeBaseName}
                  {knowledgeBaseIdLabel && (
                    <span style={{ opacity: 0.5 }}> ({knowledgeBaseIdLabel})</span>
                  )}
                </span>
              </div>
            )}
          </Space>
				</div>
				<div className="detail-page__header-actions">
					<Button 
            icon={<ArrowLeftOutlined />} 
            onClick={() => navigate(listReturnTo)}
            style={{ borderRadius: '6px' }}
          >
						返回目录
					</Button>
					<Button 
            icon={<ReloadOutlined />} 
            loading={historyQuery.isFetching}
            onClick={() => historyQuery.refetch()}
            style={{ borderRadius: '6px' }}
          >
						同步
					</Button>
				</div>
			</header>

			{/* --- Result Alerts --- */}
			{uploadResult && (
				<VersionUploadResultAlert
					result={uploadResult}
					filename={viewingVersion.filename}
					onClose={() => setUploadResult(null)}
				/>
			)}
			{rollbackResult && (
				<VersionRollbackResultAlert
					result={rollbackResult}
					filename={viewingVersion.filename}
					onClose={() => setRollbackResult(null)}
				/>
			)}

			{/* --- Main Grid --- */}
			<div className="detail-page__grid">
				<main className="detail-page__main">
					
					{/* --- Hero Status Card --- */}
					<div className="detail-hero-card">
						<div className="detail-hero-card__header">
							<div className="detail-hero-card__version">
								<span className="detail-stat-label">
                  {isViewingLatest ? "SYSTEM LATEST" : "HISTORICAL RECORD"}
                </span>
								<div className="detail-hero-card__version-num">v{viewingVersion.versionNumber}</div>
							</div>
							<VersionTags
								version={viewingVersion}
								isActive={true}
								hasBeenRolledBackAsLatest={resolveHasBeenRolledBackAsLatest(viewingVersion, versions)}
							/>
						</div>

						<div className="detail-stats-grid">
							<div className="detail-stat-item">
								<span className="detail-stat-label">文件名</span>
								<span className="detail-stat-value" style={{ fontSize: '18px', fontWeight: 700 }}>
                  {viewingVersion.filename}
                </span>
							</div>
							<div className="detail-stat-item">
								<span className="detail-stat-label">文件大小</span>
								<span className="detail-stat-value">{formatFileSize(viewingVersion.fileSize)}</span>
							</div>
              <div className="detail-stat-item">
								<span className="detail-stat-label">上传人</span>
								<span className="detail-stat-value">{getUploader(viewingVersion)}</span>
							</div>
							<div className="detail-stat-item">
								<span className="detail-stat-label">创建时间</span>
								<span className="detail-stat-value">{formatTime(viewingVersion.createdAt)}</span>
							</div>
						</div>

						<div className="detail-action-bar">
              <Button
                type="primary"
                size="large"
                className="detail-btn-emerald"
                icon={<BookOutlined />}
                onClick={() => navigate(`/ingest/documents/${encodeURIComponent(documentId)}/versions/${viewingVersion.versionNumber}/read`)}
              >
                阅读正文
              </Button>

							{canUploadNewVersion && (
								<Button
                  size="large"
									icon={<UploadOutlined />}
									onClick={() => setUploadModalOpen(true)}
								>
									上传新版本
								</Button>
							)}
							
							{!isViewingLatest && (
								<Button
                  size="large"
									icon={<ArrowLeftOutlined />}
									onClick={() => setSearchParams({})}
								>
									切回最新版本
								</Button>
							)}

              {!isViewingLatest && canRollbackVersion(viewingVersion, latestVersion) && (
								<Button
                  size="large"
									icon={<RollbackOutlined />}
									onClick={() => setRollbackTarget(viewingVersion)}
								>
									回退为最新
								</Button>
							)}

              <Button 
                danger 
                size="large"
                icon={<DeleteOutlined />} 
                onClick={() => setDeleteModalOpen(true)}
                style={{ borderRadius: '6px' }}
              >
                删除资产
              </Button>
						</div>
					</div>

					{/* --- Status & Context --- */}
          {!isViewingLatest && (
            <Alert
              className="detail-alert"
              type="warning"
              showIcon
              message="审计模式：正在查看非活动的历史版本"
              description="该版本仅供元数据核查。当前的问答系统和最新基线不受此视图影响。"
            />
          )}

					<DetailDiffSummary
						viewingVersion={viewingVersion}
						compareVersion={compareVersion}
						askableVersion={askableVersion}
					/>
				</main>

				{/* --- Side Rail --- */}
				<VersionHistoryList
					historyData={historyData}
					versions={versions}
					viewingVersion={viewingVersion}
					latestVersion={latestVersion}
					expandedHistory={expandedHistory}
					preservedReturnTo={preservedReturnTo}
					onExpandHistory={expandHistory}
					buildDetailPath={buildDetailPath}
					getUploader={getUploader}
				/>
			</div>

			{/* --- Modals --- */}
			<Modal
				title={`发布新版本 · 当前最新 v${latestVersion.versionNumber}`}
				open={uploadModalOpen}
				okText="提交"
				cancelText="取消"
        className="detail-modal"
				confirmLoading={uploadVersionMutation.isPending}
				okButtonProps={{ disabled: uploadFileList.length === 0, className: 'detail-btn-emerald' }}
				onOk={submitNewVersion}
				onCancel={() => {
					setUploadModalOpen(false);
					setUploadFileList([]);
				}}
			>
				<Space direction="vertical" size={16} style={{ width: "100%", padding: '8px 0' }}>
					<Dragger
						multiple={false}
						maxCount={1}
						fileList={uploadFileList}
						beforeUpload={() => false}
						onChange={(info) => setUploadFileList(info.fileList.slice(-1))}
						onRemove={() => setUploadFileList([])}
					>
						<p className="ant-upload-drag-icon"><InboxOutlined style={{ color: 'var(--detail-accent)' }} /></p>
						<p className="ant-upload-text">点击或拖拽文件至此区域</p>
						<p className="ant-upload-hint">新版本将覆盖 v{latestVersion.versionNumber} 成为最新的活动版本。</p>
					</Dragger>
					{uploadVersionMutation.isError && <ApiErrorAlert error={uploadVersionMutation.error} />}
				</Space>
			</Modal>

			<Modal
				title={rollbackTarget ? `确认回退至 v${rollbackTarget.versionNumber}` : "确认回退"}
				open={Boolean(rollbackTarget)}
				okText="执行回退"
				cancelText="取消"
				confirmLoading={rollbackVersionMutation.isPending}
        okButtonProps={{ danger: true }}
				onOk={submitRollbackVersion}
				onCancel={() => setRollbackTarget(null)}
			>
				<Space direction="vertical" size={16} style={{ width: "100%", padding: '8px 0' }}>
					<Alert
						type="warning"
						showIcon
						message="此操作具有破坏性"
						description={`系统将基于 v${rollbackTarget?.versionNumber} 创建一个全新的 ROLLBACK 版本，该操作不可逆，请核对文件名：${rollbackTarget?.filename}`}
					/>
					{rollbackVersionMutation.isError && <ApiErrorAlert error={rollbackVersionMutation.error} />}
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
