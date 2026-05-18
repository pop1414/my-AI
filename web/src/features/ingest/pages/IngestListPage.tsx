import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	Alert,
	Button,
	Card,
	Empty,
	Form,
	Input,
	Select,
	Space,
	Table,
	Tag,
	Tooltip,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
	SearchOutlined,
	ReloadOutlined,
	PlusOutlined,
} from "@ant-design/icons";
import { Link, useSearchParams } from "react-router-dom";
import { z } from "zod";
import {
	deleteDocument,
	listDocuments,
	type DocumentListItem,
} from "../../../shared/api/ingestApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { useAuth } from "../../../shared/auth/AuthContext";
import { DeleteDocumentConfirmModal } from "./DeleteDocumentConfirmModal";

import { DocumentStatusTag } from "../components/DocumentStatusTag";
import { DocumentTableActions } from "../components/DocumentTableActions";
import { formatTime } from "../utils/formatters";

import "./IngestListPage.css";

const filterSchema = z.object({
	kbId: z.string().optional(),
	status: z.string().optional(),
	filename: z.string().optional(),
});

const DOCUMENT_STATUSES = [
	"INGESTING",
	"INDEXED",
	"FAILED",
	"DELETING",
	"DELETED",
];

function parsePositiveInteger(value: string | null, fallback: number): number {
	const parsed = Number(value);
	return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function parseFilters(searchParams: URLSearchParams): {
	kbId?: string;
	status?: string;
	filename?: string;
} {
	return {
		kbId: searchParams.get("kbId") || undefined,
		status: searchParams.get("status") || undefined,
		filename: searchParams.get("filename") || undefined,
	};
}

function buildListSearch(params: {
	filters: { kbId?: string; status?: string; filename?: string };
	page: number;
	pageSize: number;
	deletedDocumentId?: string | null;
	deletedFilename?: string | null;
}): URLSearchParams {
	const next = new URLSearchParams();
	if (params.filters.kbId) next.set("kbId", params.filters.kbId);
	if (params.filters.status) next.set("status", params.filters.status);
	if (params.filters.filename) next.set("filename", params.filters.filename);
	if (params.page !== 1) next.set("page", String(params.page));
	if (params.pageSize !== 20) next.set("pageSize", String(params.pageSize));
	if (params.deletedDocumentId) {
		next.set("deletedDocumentId", params.deletedDocumentId);
	}
	if (params.deletedFilename) {
		next.set("deletedFilename", params.deletedFilename);
	}
	return next;
}

export function IngestListPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const queryClient = useQueryClient();
	const { user } = useAuth();
	const canAccessAdmin = Boolean(user?.capabilities.canAccessAdmin);
	const canUploadDocument = Boolean(user?.capabilities.canUploadDocument);
	const canAuditDocumentProcessing = canAccessAdmin;
	const [form] = Form.useForm<{
		kbId?: string;
		status?: string;
		filename?: string;
	}>();
	const [deleteTarget, setDeleteTarget] = useState<DocumentListItem | null>(null);
	const filters = useMemo(() => parseFilters(searchParams), [searchParams]);
	const effectiveFilters = useMemo(
		() =>
			canAuditDocumentProcessing
				? filters
				: {
						kbId: filters.kbId,
						filename: filters.filename,
					},
		[canAuditDocumentProcessing, filters],
	);
	const page = parsePositiveInteger(searchParams.get("page"), 1);
	const pageSize = parsePositiveInteger(searchParams.get("pageSize"), 20);
	const deletedDocumentId = searchParams.get("deletedDocumentId");
	const deletedFilename = searchParams.get("deletedFilename");
	const returnTo = useMemo(() => {
		const qs = buildListSearch({
			filters: effectiveFilters,
			page,
			pageSize,
		}).toString();
		return `/ingest/documents${qs ? `?${qs}` : ""}`;
	}, [effectiveFilters, page, pageSize]);

	useEffect(() => {
		form.setFieldsValue(effectiveFilters);
	}, [effectiveFilters, form]);

	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});

	const docListQuery = useQuery({
		queryKey: ["documents", effectiveFilters, page, pageSize],
		queryFn: () =>
			listDocuments({
				kbId: effectiveFilters.kbId || undefined,
				status: effectiveFilters.status || undefined,
				filename: effectiveFilters.filename || undefined,
				limit: pageSize,
				offset: (page - 1) * pageSize,
			}),
	});

	const deleteMutation = useMutation({
		mutationFn: (documentId: string) => deleteDocument(documentId),
		onSuccess: async (_, documentId) => {
			const filename = deleteTarget?.filename;
			setDeleteTarget(null);
			await queryClient.invalidateQueries({ queryKey: ["documents"] });
			setSearchParams(
				buildListSearch({
					filters: effectiveFilters,
					page,
					pageSize,
					deletedDocumentId: documentId,
					deletedFilename: filename,
				}),
			);
		},
	});

	const knowledgeBaseOptions = useMemo(
		() =>
			(knowledgeQuery.data ?? [])
				.filter((item) => canAccessAdmin || item.status === "ACTIVE")
				.map((item) => ({
					label: `${item.name}${item.status === 'INACTIVE' ? ' (已停用)' : ''} (${item.id})`,
					value: item.id,
				})),
		[knowledgeQuery.data, canAccessAdmin],
	);
	
	const onSubmit = () => {
		const values = filterSchema.parse(form.getFieldsValue());
		const nextFilters = canAuditDocumentProcessing
			? values
			: {
					kbId: values.kbId,
					filename: values.filename,
				};
		setSearchParams(
			buildListSearch({
				filters: nextFilters,
				page: 1,
				pageSize,
			}),
		);
	};

	const onReset = () => {
		form.resetFields();
		setSearchParams(new URLSearchParams());
	};

	const closeDeleteResult = () => {
		setSearchParams(
			buildListSearch({
				filters: effectiveFilters,
				page,
				pageSize,
			}),
		);
	};

	const kbNameMap = useMemo(() => {
		const map: Record<string, string> = {};
		(knowledgeQuery.data ?? []).forEach((kb) => {
			map[kb.id] = kb.name;
		});
		return map;
	}, [knowledgeQuery.data]);
	const hasActiveFilters = Boolean(
		effectiveFilters.kbId ||
			effectiveFilters.status ||
			effectiveFilters.filename,
	);

	const columns: ColumnsType<DocumentListItem> = [
		{
			title: "文档 ID",
			dataIndex: "documentId",
			width: 180,
			ellipsis: true,
			render: (value: string) => (
				<Typography.Text
					copyable={{ text: value }}
					className="ingest-document-id"
				>
					{value.length > 20 ? `${value.slice(0, 20)}…` : value}
				</Typography.Text>
			),
		},
		{
			title: "知识库",
			dataIndex: "kbId",
			width: 160,
			ellipsis: true,
			render: (val: string) => (
				<Tooltip title={`ID: ${val}`} placement="topLeft">
					<span className="ingest-kb-badge">
						{kbNameMap[val] || val}
					</span>
				</Tooltip>
			),
		},
		{
			title: "文件名",
			dataIndex: "filename",
			width: 200,
			ellipsis: true,
			render: (val) => <span className="ingest-filename">{val}</span>
		},
		{
			title: "版本",
			dataIndex: "latestVersionNumber",
			width: 80,
			align: 'center',
			render: (v: number) => (
				<Tag bordered={false} className="ingest-version-tag">
					v{v}
				</Tag>
			),
		},
		...(canAuditDocumentProcessing
			? [
					{
						title: "处理状态",
						dataIndex: "status",
						width: 120,
						render: (value: string) => <DocumentStatusTag status={value} />,
					},
				]
			: []),
		{
			title: "最后更新",
			dataIndex: "updatedAt",
			width: 160,
			render: (value: string) => (
				<span className="ingest-update-time">
					{formatTime(value)}
				</span>
			),
		},
		{
			title: "操作",
			key: "action",
			width: 320,
			fixed: 'right',
			render: (_, record) => (
				<DocumentTableActions
					record={record}
					canAccessAdmin={canAccessAdmin}
					returnTo={returnTo}
					onDelete={setDeleteTarget}
				/>
			),
		},
	];


	const dataSource = docListQuery.data?.items ?? [];
	const total = docListQuery.data?.total ?? 0;

	return (
		<div className="ingest-list-container">
			{/* 头部标题与操作 */}
			<div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
				<div>
					<Typography.Title level={3} className="console-page-title">
						文档目录
					</Typography.Title>
					<Typography.Text type="secondary">
						{canAuditDocumentProcessing
							? "按知识库、当前最新版本状态或文件名筛选，浏览所有已上传文档的处理进度。"
							: "按可访问知识库或文件名筛选，进入当前文档的问答基线阅读界面。"}
					</Typography.Text>
				</div>
				{canUploadDocument && (
					<Link to="/ingest/upload">
						<Button type="primary" icon={<PlusOutlined />} size="large">
							上传文档
						</Button>
					</Link>
				)}
			</div>

			{/* 筛选区域 */}
			<Card className="ingest-filter-card">
				<Form
					form={form}
					layout="inline"
					className="ingest-filter-form"
					onFinish={onSubmit}
				>
					<Form.Item name="kbId" label="所属知识库" className="ingest-filter-item" style={{ minWidth: 240 }}>
						<Select
							aria-label="按知识库筛选文档"
							allowClear
							placeholder="全部知识库"
							loading={knowledgeQuery.isLoading}
							options={knowledgeBaseOptions}
						/>
					</Form.Item>
					{canAuditDocumentProcessing && (
						<Form.Item name="status" label="处理状态" className="ingest-filter-item" style={{ minWidth: 180 }}>
							<Select
								aria-label="按处理状态筛选文档"
								allowClear
								placeholder="全部状态"
								options={DOCUMENT_STATUSES.map((s) => ({
									label: s,
									value: s,
								}))}
							/>
						</Form.Item>
					)}
					<Form.Item name="filename" label="搜索" className="ingest-filter-item" style={{ minWidth: 260 }}>
						<Input
							aria-label="按文件名搜索文档"
							autoComplete="off"
							allowClear
							placeholder="文件名模糊匹配"
							prefix={<SearchOutlined style={{ color: 'var(--console-ink-faint)' }} />}
						/>
					</Form.Item>
					<Form.Item className="ingest-filter-item">
						<Space size={8}>
							<Button
								type="primary"
								htmlType="submit"
								loading={docListQuery.isFetching}
							>
								查询
							</Button>
							<Button icon={<ReloadOutlined />} onClick={onReset}>
								重置
							</Button>
						</Space>
					</Form.Item>
				</Form>
			</Card>

			{/* 错误提示 */}
			{knowledgeQuery.isError && (
				<ApiErrorAlert error={knowledgeQuery.error} />
			)}
			{docListQuery.isError && (
				<ApiErrorAlert error={docListQuery.error} />
			)}
			{deletedDocumentId && (
				<Alert
					className="ingest-delete-result"
					data-testid="document-delete-result"
					aria-live="polite"
					aria-atomic="true"
					type="success"
					showIcon
					message="文档资产已删除"
					description={
						<div style={{ marginTop: 8 }}>
							<div style={{ marginBottom: 4 }}>
								<strong>文件名：</strong>
								<span className="ingest-filename">{deletedFilename || "未知"}</span>
							</div>
							<div style={{ marginBottom: 12 }}>
								<strong>文档 ID：</strong>
								<Typography.Text code style={{ fontSize: 12 }}>{deletedDocumentId}</Typography.Text>
							</div>
							<Typography.Text type="secondary" style={{ fontSize: 13 }}>
								该文档及其所有版本已从系统中移除。如需再次使用，请重新上传。
							</Typography.Text>
						</div>
					}
					action={
						<Button size="small" type="text" onClick={closeDeleteResult}>
							关闭提示
						</Button>
					}
				/>
			)}

			{/* 数据表格 */}
			<Card className="ingest-table-card">
				{docListQuery.isLoading ? (
					<Table<DocumentListItem>
						loading
						columns={columns}
						dataSource={[]}
						rowKey="documentId"
						pagination={false}
						className="ingest-table"
					/>
				) : dataSource.length === 0 ? (
					<div className="ingest-empty-state">
						<Empty
							description={
								hasActiveFilters
									? "未找到符合当前筛选条件的文档，请尝试调整筛选条件。"
									: "暂无文档，请先上传文档开始使用。"
							}
						>
							{!hasActiveFilters && canUploadDocument && (
								<Link to="/ingest/upload">
									<Button type="primary" icon={<PlusOutlined />}>
										立即上传
									</Button>
								</Link>
							)}
						</Empty>
					</div>
				) : (
					<Table<DocumentListItem>
						rowKey="documentId"
						columns={columns}
						dataSource={dataSource}
						loading={docListQuery.isFetching}
						scroll={{ x: canAuditDocumentProcessing ? 1400 : 1280 }}
						className="ingest-table"
						pagination={{
							current: page,
							pageSize: pageSize,
							total: total,
							showSizeChanger: true,
							showQuickJumper: true,
							pageSizeOptions: ["10", "20", "50", "100"],
							showTotal: (t, range) =>
								`共 ${t} 条文档，当前 ${range[0]}-${range[1]}`,
							onChange: (p, ps) => {
								setSearchParams(
									buildListSearch({
										filters: effectiveFilters,
										page: p,
										pageSize: ps,
									}),
								);
							},
						}}
					/>
				)}
			</Card>
			<DeleteDocumentConfirmModal
				open={Boolean(deleteTarget)}
				document={deleteTarget}
				confirmLoading={deleteMutation.isPending}
				error={deleteMutation.error}
				onCancel={() => setDeleteTarget(null)}
				onConfirm={() => {
					if (deleteTarget) {
						deleteMutation.mutate(deleteTarget.documentId);
					}
				}}
			/>
		</div>
	);
}
