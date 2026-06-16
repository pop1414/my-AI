import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
	Button,
	Card,
	Form,
	Input,
	InputNumber,
	Space,
	Table,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useNavigate, useParams } from "react-router-dom";
import { z } from "zod";
import {
	getDocumentChunksPreview,
	getDocumentStatus,
	type DocumentChunksPreviewResponse,
} from "../../../shared/api/ingestApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

const { Title, Text } = Typography;

const previewFormSchema = z.object({
	documentId: z.string().trim().min(1, "documentId 不能为空"),
	limit: z.number().int().min(1).max(200),
	offset: z.number().int().min(0).max(100000),
	previewChars: z.number().int().min(20).max(2000),
});

type QueryInput = {
	documentId: string;
	limit: number;
	offset: number;
	previewChars: number;
};

const columns: ColumnsType<DocumentChunksPreviewResponse["chunks"][number]> = [
	{ title: "chunkIndex", dataIndex: "chunkIndex", width: 100 },
	{ title: "contentLength", dataIndex: "contentLength", width: 120 },
	{
		title: "truncated",
		dataIndex: "truncated",
		width: 110,
		render: (value: boolean) => (value ? "是" : "否"),
	},
	{ title: "contentPreview", dataIndex: "contentPreview" },
	{ title: "sourceFile", dataIndex: "sourceFile", width: 180 },
	{
		title: "sourceHint",
		dataIndex: "sourceHint",
		width: 220,
		render: (value?: string | null) => value ?? "-",
	},
	{ title: "splitVersion", dataIndex: "splitVersion", width: 120 },
	{ title: "contentHash", dataIndex: "contentHash", width: 260 },
];

export function IngestChunksPreviewPage() {
	const navigate = useNavigate();
	const { documentId: urlDocumentId } = useParams<{ documentId?: string }>();
	const [form] = Form.useForm<QueryInput>();
	const initialDocumentId =
		urlDocumentId ?? localStorage.getItem("myai:lastDocumentId") ?? "";
	const [queryInput, setQueryInput] = useState<QueryInput | null>(() => {
		const docId = urlDocumentId ?? localStorage.getItem("myai:lastDocumentId");
		if (docId) {
			return { documentId: docId, limit: 20, offset: 0, previewChars: 200 };
		}
		return null;
	});

	// URL 带 documentId 时同步表单字段
	useEffect(() => {
		if (urlDocumentId && queryInput) {
			form.setFieldsValue(queryInput);
		}
	}, [urlDocumentId]); // eslint-disable-line react-hooks/exhaustive-deps

	const docStatusQuery = useQuery({
		queryKey: ["document-status", queryInput?.documentId],
		queryFn: () => getDocumentStatus(queryInput!.documentId),
		enabled: !!queryInput?.documentId,
	});

	const kbQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});

	const previewQuery = useQuery({
		queryKey: ["ingest-chunks-preview", queryInput],
		queryFn: () => getDocumentChunksPreview(queryInput!),
		enabled: queryInput !== null,
	});

	const onSubmit = () => {
		const values = previewFormSchema.parse(form.getFieldsValue());
		setQueryInput(values);
	};

  const kbName = useMemo(() => {
    const kbId = docStatusQuery.data?.kbId;
    return kbId ? kbQuery.data?.find(kb => kb.id === kbId)?.name : undefined;
  }, [docStatusQuery.data?.kbId, kbQuery.data]);

	return (
		<Space direction="vertical" size={16} style={{ width: "100%" }}>
			<Card
				title={
					<Space direction="vertical" size={2}>
						<Title level={4} style={{ margin: 0 }}>
							分块预览 · {docStatusQuery.data?.latestFilename || (queryInput?.documentId ? queryInput.documentId.slice(0, 12) + '...' : "文档分块预览")}
						</Title>
						{queryInput?.documentId && (
							<Text type="secondary" style={{ fontSize: 12, fontFamily: 'var(--console-font-mono)' }}>
								ID: {queryInput.documentId}
							</Text>
						)}
            {kbName && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                知识库: {kbName}
              </Text>
            )}
					</Space>
				}
				extra={
					<Space>
						<Button
							className="console-return-button"
							onClick={() => navigate("/ingest/documents")}
						>
							返回列表
						</Button>
						<Text type="secondary" style={{ fontSize: 12 }}>
							{`GET /api/v1/documents/${queryInput?.documentId || '{id}'}/chunks`}
						</Text>
					</Space>
				}
			>
				<Form
					form={form}
					layout="inline"
					initialValues={{
						documentId: initialDocumentId,
						limit: 20,
						offset: 0,
						previewChars: 200,
					}}
					onFinish={onSubmit}
				>
					<Form.Item name="documentId" style={{ minWidth: 260 }}>
						<Input placeholder="documentId" allowClear />
					</Form.Item>
					<Form.Item name="limit">
						<InputNumber min={1} max={200} addonBefore="limit" />
					</Form.Item>
					<Form.Item name="offset">
						<InputNumber
							min={0}
							max={100000}
							addonBefore="offset"
						/>
					</Form.Item>
					<Form.Item name="previewChars">
						<InputNumber
							min={20}
							max={2000}
							addonBefore="previewChars"
						/>
					</Form.Item>
					<Button
						type="primary"
						htmlType="submit"
						loading={previewQuery.isFetching}
					>
						查询预览
					</Button>
				</Form>
			</Card>

			{previewQuery.isError && (
				<ApiErrorAlert error={previewQuery.error} />
			)}
			{docStatusQuery.isError && (
				<ApiErrorAlert error={docStatusQuery.error} />
			)}
      {kbQuery.isError && (
				<ApiErrorAlert error={kbQuery.error} />
			)}

			{previewQuery.data && (
				<Card
					title={`本页分块：${previewQuery.data.chunkCount} / 总分块：${previewQuery.data.totalChunks}`}
					extra={
						<Typography.Text type="secondary">
							limit={previewQuery.data.limit}, offset=
							{previewQuery.data.offset}, previewChars=
							{previewQuery.data.previewChars}
						</Typography.Text>
					}
				>
					<Table
						rowKey={(row) => `${row.chunkIndex}-${row.contentHash}`}
						columns={columns}
						dataSource={previewQuery.data.chunks}
						pagination={{
							current:
								Math.floor(
									previewQuery.data.offset /
										previewQuery.data.limit,
								) + 1,
							pageSize: previewQuery.data.limit,
							total: previewQuery.data.totalChunks,
							showSizeChanger: true,
							pageSizeOptions: [10, 20, 50, 100, 200],
							onChange: (page, pageSize) => {
								if (!queryInput) {
									return;
								}
								const nextInput = {
									...queryInput,
									limit: pageSize,
									offset: (page - 1) * pageSize,
								};
								setQueryInput(nextInput);
								form.setFieldsValue(nextInput);
							},
						}}
						scroll={{ x: 1400 }}
					/>
				</Card>
			)}
		</Space>
	);
}
