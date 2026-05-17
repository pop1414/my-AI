import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
	Button,
	Card,
	Form,
	Input,
	Space,
	Switch,
	Tag,
	Typography,
} from "antd";
import { useNavigate, useParams } from "react-router-dom";
import { z } from "zod";
import { getDocumentStatus } from "../../../shared/api/ingestApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import "./IngestStatusPage.css";

const { Text } = Typography;

const statusFormSchema = z.object({
	documentId: z.string().trim().min(1, "documentId 不能为空"),
});

const terminalStatuses = new Set(["INDEXED", "FAILED", "DELETED"]);

function statusColor(status?: string): string {
	switch (status) {
		case "UPLOADED":
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

export function IngestStatusPage() {
	const navigate = useNavigate();
	const { documentId: urlDocumentId } = useParams<{ documentId?: string }>();
	const [form] = Form.useForm<{ documentId: string }>();
	const initialDocumentId =
		urlDocumentId ?? localStorage.getItem("myai:lastDocumentId") ?? "";
	const [targetDocumentId, setTargetDocumentId] =
		useState<string>(initialDocumentId);
	const [autoRefresh, setAutoRefresh] = useState(true);

	// 如果 URL 带了 documentId，自动开始轮询
	const effectiveDocumentId = urlDocumentId ?? targetDocumentId;

	const statusQuery = useQuery({
		queryKey: ["ingest-status", effectiveDocumentId],
		queryFn: () => getDocumentStatus(effectiveDocumentId),
		enabled: effectiveDocumentId.length > 0,
		refetchInterval: (query) => {
			if (!autoRefresh) {
				return false;
			}
			const currentStatus = query.state.data?.status;
			if (currentStatus && terminalStatuses.has(currentStatus)) {
				return false;
			}
			return 3000;
		},
	});

  const kbQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});

	const currentStatus = statusQuery.data?.status;
	const isTerminal = useMemo(
		() => (currentStatus ? terminalStatuses.has(currentStatus) : false),
		[currentStatus],
	);

	const onSubmit = () => {
		const values = statusFormSchema.parse(form.getFieldsValue());
		setTargetDocumentId(values.documentId);
		localStorage.setItem("myai:lastDocumentId", values.documentId);
	};

  const kbName = useMemo(() => {
    const kbId = statusQuery.data?.kbId;
    return kbId ? kbQuery.data?.find(kb => kb.id === kbId)?.name : undefined;
  }, [statusQuery.data?.kbId, kbQuery.data]);

	return (
		<Space
			direction="vertical"
			size={16}
			style={{ width: "100%" }}
			className="status-page"
		>
			<Card
				className="status-page__card status-page__card--hero"
				title="查询文档状态"
				extra={
					<Space>
						<Button
							className="console-return-button"
							onClick={() => navigate("/ingest/documents")}
						>
							返回文档列表
						</Button>
						<Typography.Text type="secondary">
							GET /api/v1/documents/{"{documentId}"}/status
						</Typography.Text>
					</Space>
				}
			>
				<Form
					form={form}
					layout="inline"
					className="status-page__inline-form"
					initialValues={{ documentId: initialDocumentId }}
					onFinish={onSubmit}
				>
					<Form.Item
						name="documentId"
						style={{ flex: 1, minWidth: 320 }}
					>
						<Input placeholder="输入 documentId" allowClear />
					</Form.Item>
					<Button type="primary" htmlType="submit">
						查询
					</Button>
				</Form>
			</Card>

			<Card
				className="status-page__card"
				title="轮询控制"
				extra={
					<Space>
						<span>自动刷新</span>
						<Switch
							checked={autoRefresh}
							onChange={setAutoRefresh}
						/>
					</Space>
				}
			>
				<Space>
					<Button
						onClick={() => statusQuery.refetch()}
						loading={statusQuery.isFetching}
						disabled={!targetDocumentId}
					>
						立即刷新
					</Button>
					{isTerminal && (
						<Tag color="success">已到达终态，自动轮询停止</Tag>
					)}
				</Space>
			</Card>

			{statusQuery.isError && <ApiErrorAlert error={statusQuery.error} />}
      {kbQuery.isError && <ApiErrorAlert error={kbQuery.error} />}

			{statusQuery.data && (
				<Card title="当前状态" className="status-page__card">
					<p>
						<strong>文件名:</strong>{" "}
						<span className="ingest-filename">{statusQuery.data.latestFilename}</span>
					</p>
          {kbName && (
            <p>
              <strong>知识库:</strong>{" "}
              <Text>{kbName}</Text>
            </p>
          )}
					<p>
						<strong>documentId:</strong>{" "}
						<Typography.Text code style={{ fontSize: 12 }}>{statusQuery.data.documentId}</Typography.Text>
					</p>
					<p>
						<strong>status:</strong>{" "}
						<Tag color={statusColor(statusQuery.data.status)}>
							{statusQuery.data.status}
						</Tag>
					</p>
					<Space className="status-page__actions">
						{(statusQuery.data.status === "FAILED" ||
							statusQuery.data.status === "INDEXED") && (
							<Button
								type="default"
								onClick={() => {
									navigate(
										`/ingest/documents/${encodeURIComponent(statusQuery.data!.documentId)}/reprocess`,
									);
								}}
							>
								去重处理
							</Button>
						)}
						{statusQuery.data.status !== "DELETED" &&
							statusQuery.data.status !== "DELETING" && (
								<Button
									danger
									type="default"
									onClick={() => {
										navigate(
											`/ingest/documents/${encodeURIComponent(statusQuery.data!.documentId)}/delete`,
										);
									}}
								>
									去删除
								</Button>
							)}
					</Space>
				</Card>
			)}
		</Space>
	);
}
