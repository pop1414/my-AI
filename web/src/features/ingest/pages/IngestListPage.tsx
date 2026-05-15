import { type ReactNode, useEffect, useMemo, useState } from "react";
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
	DeleteOutlined,
	FileSearchOutlined,
	FileSyncOutlined,
	SearchOutlined,
	ReloadOutlined,
} from "@ant-design/icons";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { z } from "zod";
import {
	deleteDocument,
	listDocuments,
	type DocumentListItem,
} from "../../../shared/api/ingestApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { useAuth } from "../../../shared/auth/AuthContext";
import { SafetyOutlined } from "@ant-design/icons";
import { DeleteDocumentConfirmModal } from "./DeleteDocumentConfirmModal";

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

function statusColor(status: string): string {
	switch (status) {
		case "UPLOADED":
		case "ACCEPTED":
			return "blue";
		case "INGESTING":
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

function formatFileSize(bytes: number): string {
	if (bytes < 1024) return `${bytes} B`;
	if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
	return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatTime(iso: string): string {
	try {
		return new Date(iso).toLocaleString("zh-CN", {
			year: "numeric",
			month: "2-digit",
			day: "2-digit",
			hour: "2-digit",
			minute: "2-digit",
		});
	} catch {
		return iso;
	}
}

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
	return next;
}

function buildReturnTo(locationSearch: string): string {
	const params = new URLSearchParams(locationSearch);
	params.delete("deletedDocumentId");
	const qs = params.toString();
	return `/ingest/documents${qs ? `?${qs}` : ""}`;
}

function ActionIconLink({
	to,
	label,
	icon,
}: {
	to: string;
	label: string;
	icon: ReactNode;
}) {
	return (
		<Link
			to={to}
			aria-label={label}
			style={{
				display: "inline-flex",
				alignItems: "center",
				justifyContent: "center",
				width: 24,
				height: 24,
				border: "1px solid #d9d9d9",
				borderRadius: 6,
				color: "rgba(0, 0, 0, 0.88)",
				background: "#ffffff",
			}}
		>
			{icon}
		</Link>
	);
}

export function IngestListPage() {
	const location = useLocation();
	const [searchParams, setSearchParams] = useSearchParams();
	const queryClient = useQueryClient();
	const { user } = useAuth();
	const canAccessAdmin = Boolean(user?.capabilities.canAccessAdmin);
	const [form] = Form.useForm<{
		kbId?: string;
		status?: string;
		filename?: string;
	}>();
	const [deleteTarget, setDeleteTarget] = useState<DocumentListItem | null>(null);
	const filters = useMemo(() => parseFilters(searchParams), [searchParams]);
	const page = parsePositiveInteger(searchParams.get("page"), 1);
	const pageSize = parsePositiveInteger(searchParams.get("pageSize"), 20);
	const deletedDocumentId = searchParams.get("deletedDocumentId");
	const returnTo = buildReturnTo(location.search);

	useEffect(() => {
		form.setFieldsValue(filters);
	}, [filters, form]);

	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});

	const docListQuery = useQuery({
		queryKey: ["documents", filters, page, pageSize],
		queryFn: () =>
			listDocuments({
				kbId: filters.kbId || undefined,
				status: filters.status || undefined,
				filename: filters.filename || undefined,
				limit: pageSize,
				offset: (page - 1) * pageSize,
			}),
	});

	const deleteMutation = useMutation({
		mutationFn: (documentId: string) => deleteDocument(documentId),
		onSuccess: async (_, documentId) => {
			setDeleteTarget(null);
			await queryClient.invalidateQueries({ queryKey: ["documents"] });
			setSearchParams(
				buildListSearch({
					filters,
					page,
					pageSize,
					deletedDocumentId: documentId,
				}),
			);
		},
	});

	const knowledgeBaseOptions = useMemo(
		() =>
			(knowledgeQuery.data ?? [])
				.filter((item) => item.status === "ACTIVE")
				.map((item) => ({
					label: `${item.name} (${item.id})`,
					value: item.id,
				})),
		[knowledgeQuery.data],
	);
	const onSubmit = () => {
		const values = filterSchema.parse(form.getFieldsValue());
		setSearchParams(
			buildListSearch({
				filters: values,
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
				filters,
				page,
				pageSize,
			}),
		);
	};

	const columns: ColumnsType<DocumentListItem> = [
		{
			title: "文档ID",
			dataIndex: "documentId",
			width: 200,
			ellipsis: true,
			render: (value: string) => (
				<Tooltip title={value}>
					<Typography.Text
						copyable={{ text: value }}
						style={{ fontSize: 12 }}
					>
						{value.length > 24 ? `${value.slice(0, 24)}…` : value}
					</Typography.Text>
				</Tooltip>
			),
		},
		{ title: "知识库", dataIndex: "kbId", width: 160, ellipsis: true },
		{
			title: "最新文件名",
			dataIndex: "filename",
			width: 220,
			ellipsis: true,
		},
		{
			title: "最新版本",
			dataIndex: "latestVersionNumber",
			width: 120,
			render: (_: number, record) => (
				<Space size={4} wrap>
					<Tag color="gold">v{record.latestVersionNumber}</Tag>
					<Tag>{record.latestVersionOriginType}</Tag>
				</Space>
			),
		},
		{
			title: "大小",
			dataIndex: "fileSize",
			width: 90,
			render: (value: number) => formatFileSize(value),
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 120,
			render: (value: string) => (
				<Tag color={statusColor(value)}>{value}</Tag>
			),
		},
		{
			title: "失败原因",
			dataIndex: "failureReason",
			width: 160,
			ellipsis: true,
			render: (value?: string | null) =>
				value ? (
					<Tooltip title={value}>
						<Typography.Text type="danger" style={{ fontSize: 12 }}>
							{value.length > 20
								? `${value.slice(0, 20)}…`
								: value}
						</Typography.Text>
					</Tooltip>
				) : (
					"-"
				),
		},
		{
			title: "创建时间",
			dataIndex: "createdAt",
			width: 150,
			render: (value: string) => formatTime(value),
		},
		{
			title: "更新时间",
			dataIndex: "updatedAt",
			width: 150,
			render: (value: string) => formatTime(value),
		},
		{
			title: "操作",
			key: "action",
			width: 280,
			fixed: "right",
			render: (_, record) => {
				const showChunksPreview = record.status === "INDEXED";
				const showReprocess =
					record.status === "FAILED" || record.status === "INDEXED";
				const showDelete =
					record.status !== "DELETED" && record.status !== "DELETING";

				return (
					<Space size="small" wrap>
						<Tooltip title="查看详情">
							<ActionIconLink
								label="查看详情"
								icon={<SearchOutlined />}
								to={`/ingest/documents/${encodeURIComponent(record.documentId)}?returnTo=${encodeURIComponent(returnTo)}`}
							/>
						</Tooltip>
						{showChunksPreview && (
							<Tooltip title="分块预览">
								<ActionIconLink
									label="分块预览"
									icon={<FileSearchOutlined />}
									to={`/ingest/documents/${encodeURIComponent(record.documentId)}/chunks-preview`}
								/>
							</Tooltip>
						)}
						{showReprocess && (
							<Tooltip title="重处理">
								<ActionIconLink
									label="重处理"
									icon={<FileSyncOutlined />}
									to={`/ingest/documents/${encodeURIComponent(record.documentId)}/reprocess`}
								/>
							</Tooltip>
						)}
						{showDelete && (
							<Tooltip title="删除">
							<Button
								size="small"
								danger
								aria-label="删除"
								icon={<DeleteOutlined />}
								onClick={() => setDeleteTarget(record)}
							/>
						</Tooltip>
					)}
						{canAccessAdmin && (
							<Tooltip title="授权管理">
								<ActionIconLink
									label="授权管理"
									icon={<SafetyOutlined />}
									to={`/admin/documents/${encodeURIComponent(record.documentId)}/grants`}
								/>
							</Tooltip>
						)}
					</Space>
				);
			},
		},
	];

	const dataSource = docListQuery.data?.items ?? [];
	const total = docListQuery.data?.total ?? 0;

	return (
		<Space direction="vertical" size={16} style={{ width: "100%" }}>
			{/* 筛选区域 */}
			<Card
				title="文档列表"
				extra={
					<Typography.Text type="secondary">
						GET /api/v1/documents
					</Typography.Text>
				}
			>
				<Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
					按知识库、当前最新版本状态或文件名筛选，浏览所有已上传文档的处理进度。
				</Typography.Paragraph>

				<Form
					form={form}
					layout="inline"
					style={{ flexWrap: "wrap", gap: 8 }}
					onFinish={onSubmit}
				>
					<Form.Item name="kbId" style={{ minWidth: 200 }}>
						<Select
							aria-label="按知识库筛选文档"
							allowClear
							placeholder="选择知识库"
							loading={knowledgeQuery.isLoading}
							options={knowledgeBaseOptions}
						/>
					</Form.Item>
					<Form.Item name="status" style={{ minWidth: 150 }}>
						<Select
							aria-label="按处理状态筛选文档"
							allowClear
							placeholder="处理状态"
							options={DOCUMENT_STATUSES.map((s) => ({
								label: s,
								value: s,
							}))}
						/>
					</Form.Item>
					<Form.Item name="filename" style={{ minWidth: 200 }}>
						<Input
							aria-label="按文件名搜索文档"
							autoComplete="off"
							allowClear
							placeholder="搜索文件名（模糊匹配）"
						/>
					</Form.Item>
					<Form.Item>
						<Space>
							<Button
								type="primary"
								htmlType="submit"
								icon={<SearchOutlined />}
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
					data-testid="document-delete-result"
					aria-live="polite"
					aria-atomic="true"
					type="success"
					showIcon
					message="document 资产已删除"
					description={`旧 documentId：${deletedDocumentId}。同内容重新上传会生成新的 documentId，新文档不会继承旧文档级授权；如需继续使用，请重新上传并重新配置授权。`}
					action={
						<Space wrap>
							<Link to="/ingest/upload">上传新文档</Link>
							<Button size="small" type="text" onClick={closeDeleteResult}>
								关闭提示
							</Button>
						</Space>
					}
				/>
			)}

			{/* 数据表格 */}
			<Card>
				{docListQuery.isLoading ? (
					<Table<DocumentListItem>
						loading
						columns={columns}
						dataSource={[]}
						rowKey="documentId"
						pagination={false}
					/>
				) : dataSource.length === 0 ? (
					<Empty
						description={
							Object.keys(filters).length > 0
								? "未找到符合当前筛选条件的文档，请尝试调整筛选条件。"
								: "暂无文档，请先上传文档开始使用。"
						}
					/>
				) : (
					<Table<DocumentListItem>
						rowKey="documentId"
						columns={columns}
						dataSource={dataSource}
						loading={docListQuery.isFetching}
						scroll={{ x: 1500 }}
						pagination={{
							current: page,
							pageSize: pageSize,
							total: total,
							showSizeChanger: true,
							showQuickJumper: true,
							pageSizeOptions: [10, 20, 50, 100],
							showTotal: (t, range) =>
								`共 ${t} 条文档，当前 ${range[0]}-${range[1]}`,
							onChange: (p, ps) => {
								setSearchParams(
									buildListSearch({
										filters,
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
		</Space>
	);
}
