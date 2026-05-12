import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Alert, Button, Card, Form, Input, Space, Tag, Typography, message } from "antd";
import { useNavigate, useParams } from "react-router-dom";
import { z } from "zod";
import {
	reprocessDocument,
	type DocumentStatusResponse,
} from "../../../shared/api/ingestApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { ApiError } from "../../../shared/api/request";

const reprocessFormSchema = z.object({
	documentId: z.string().trim().min(1, "documentId 不能为空"),
});

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
		default:
			return "default";
	}
}

export function IngestReprocessPage() {
	const navigate = useNavigate();
	const { documentId: urlDocumentId } = useParams<{ documentId?: string }>();
	const [form] = Form.useForm<{ documentId: string }>();
	const [result, setResult] = useState<DocumentStatusResponse | null>(null);
	const initialDocumentId =
		urlDocumentId ?? localStorage.getItem("myai:lastDocumentId") ?? "";

	const mutation = useMutation({
		mutationFn: (documentId: string) => reprocessDocument(documentId),
		onSuccess: (data) => {
			setResult(data);
			localStorage.setItem("myai:lastDocumentId", data.documentId);
			message.success("文档已重新进入处理队列");
		},
	});

	const onSubmit = async () => {
		const values = reprocessFormSchema.parse(form.getFieldsValue());
		await mutation.mutateAsync(values.documentId);
	};

	const conflictWarning =
		mutation.error instanceof ApiError && mutation.error.status === 409 ? (
			<Alert
				type="warning"
				showIcon
				message="当前文档状态不允许重处理（通常是正在 INGESTING 或不在可重处理状态）。"
			/>
		) : null;

	return (
		<Space direction="vertical" size={16} style={{ width: "100%" }}>
			<Card
				title="触发文档重处理"
				extra={
					<Space>
						<Button onClick={() => navigate("/ingest/documents")}>
							返回文档列表
						</Button>
						<Typography.Text type="secondary">
							POST /api/v1/documents/{"{documentId}"}/reprocess
						</Typography.Text>
					</Space>
				}
			>
				<Form
					form={form}
					layout="inline"
					initialValues={{
						documentId: initialDocumentId,
					}}
					onFinish={onSubmit}
				>
					<Form.Item
						name="documentId"
						style={{ flex: 1, minWidth: 320 }}
					>
						<Input placeholder="输入 documentId" allowClear />
					</Form.Item>
					<Button
						type="primary"
						htmlType="submit"
						loading={mutation.isPending}
					>
						触发重处理
					</Button>
				</Form>
			</Card>

			{mutation.isError && <ApiErrorAlert error={mutation.error} />}
			{conflictWarning}

			{result && (
				<Card title="重处理结果">
					<p>
						<strong>documentId:</strong> {result.documentId}
					</p>
					<p>
						<strong>status:</strong>{" "}
						<Tag color={statusColor(result.status)}>
							{result.status}
						</Tag>
					</p>
					<Typography.Text type="secondary">
						当前接口返回 UPLOADED，表示已重新进入待处理队列，后续由
						worker 抢占执行。
					</Typography.Text>
				</Card>
			)}
		</Space>
	);
}
