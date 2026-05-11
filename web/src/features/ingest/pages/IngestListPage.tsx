import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
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
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import {
	listDocuments,
	type DocumentListItem,
} from "../../../shared/api/ingestApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { useAuth } from "../../../shared/auth/AuthContext";
import { SafetyOutlined } from "@ant-design/icons";

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

export function IngestListPage() {
	const navigate = useNavigate();
	const { user } = useAuth();
	const canAccessAdmin = Boolean(user?.capabilities.canAccessAdmin);
	const [form] = Form.useForm<{
		kbId?: string;
		status?: string;
		filename?: string;
	}>();
	const [page, setPage] = useState(1);
	const [pageSize, setPageSize] = useState(20);
	const [filters, setFilters] = useState<{
		kbId?: string;
		status?: string;
		filename?: string;
	}>({});

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
		setFilters(values);
		setPage(1);
	};

	const onReset = () => {
		form.resetFields();
		setFilters({});
		setPage(1);
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
		{ title: "文件名", dataIndex: "filename", width: 200, ellipsis: true },
		{
			title: "大小",
			dataIndex: "fileSize",
			width: 90,
			render: (value: number) => formatFileSize(value),
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
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
						<Tooltip title="查看状态">
							<Button
								size="small"
								icon={<SearchOutlined />}
								onClick={() =>
									navigate(
										`/ingest/documents/${encodeURIComponent(record.documentId)}/status`,
									)
								}
							/>
						</Tooltip>
						{showChunksPreview && (
							<Tooltip title="分块预览">
								<Button
									size="small"
									icon={<FileSearchOutlined />}
									onClick={() =>
										navigate(
											`/ingest/documents/${encodeURIComponent(record.documentId)}/chunks-preview`,
										)
									}
								/>
							</Tooltip>
						)}
						{showReprocess && (
							<Tooltip title="重处理">
								<Button
									size="small"
									icon={<FileSyncOutlined />}
									onClick={() =>
										navigate(
											`/ingest/documents/${encodeURIComponent(record.documentId)}/reprocess`,
										)
									}
								/>
							</Tooltip>
						)}
						{showDelete && (
							<Tooltip title="删除">
								<Button
									size="small"
									danger
									icon={<DeleteOutlined />}
									onClick={() =>
										navigate(
											`/ingest/documents/${encodeURIComponent(record.documentId)}/delete`,
										)
									}
								/>
							</Tooltip>
						)}
						{canAccessAdmin && (
							<Tooltip title="授权管理">
								<Button
									size="small"
									icon={<SafetyOutlined />}
									onClick={() =>
										navigate(
											`/admin/documents/${encodeURIComponent(record.documentId)}/grants`,
										)
									}
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
					按知识库、处理状态或文件名筛选，浏览所有已上传文档的处理进度。
				</Typography.Paragraph>

				<Form
					form={form}
					layout="inline"
					style={{ flexWrap: "wrap", gap: 8 }}
					onFinish={onSubmit}
				>
					<Form.Item name="kbId" style={{ minWidth: 200 }}>
						<Select
							allowClear
							placeholder="选择知识库"
							loading={knowledgeQuery.isLoading}
							options={knowledgeBaseOptions}
						/>
					</Form.Item>
					<Form.Item name="status" style={{ minWidth: 150 }}>
						<Select
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
								setPage(p);
								setPageSize(ps);
							},
						}}
					/>
				)}
			</Card>
		</Space>
	);
}
