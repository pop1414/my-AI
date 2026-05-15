import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Alert, Button, Card, Form, Input, Space, Typography } from "antd";
import { useNavigate, useParams } from "react-router-dom";
import { z } from "zod";
import { deleteDocument } from "../../../shared/api/ingestApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { ApiError } from "../../../shared/api/request";
import { DeleteDocumentConfirmModal } from "./DeleteDocumentConfirmModal";

const deleteFormSchema = z.object({
	documentId: z.string().trim().min(1, "documentId 不能为空"),
});

export function IngestDeletePage() {
	const navigate = useNavigate();
	const { documentId: urlDocumentId } = useParams<{ documentId?: string }>();
	const [form] = Form.useForm<{ documentId: string }>();
	const [confirmDocumentId, setConfirmDocumentId] = useState<string | null>(null);
	const initialDocumentId =
		urlDocumentId ?? localStorage.getItem("myai:lastDocumentId") ?? "";

	const deleteMutation = useMutation({
		mutationFn: (documentId: string) => deleteDocument(documentId),
		onSuccess: (_, documentId) => {
			localStorage.setItem("myai:lastDocumentId", documentId);
			navigate(
				`/ingest/documents?deletedDocumentId=${encodeURIComponent(documentId)}`,
				{ replace: true },
			);
		},
	});

	const openDeleteConfirm = () => {
		const values = deleteFormSchema.parse(form.getFieldsValue());
		setConfirmDocumentId(values.documentId);
	};

	const conflictWarning =
		deleteMutation.error instanceof ApiError &&
		deleteMutation.error.status === 409 ? (
			<Alert
				type="warning"
				showIcon
				message="当前文档状态不允许删除（通常是 INGESTING 或 DELETING）。"
			/>
		) : null;

	return (
		<Space direction="vertical" size={16} style={{ width: "100%" }}>
			<Card
				title="删除文档资产"
				extra={
					<Space>
						<Button onClick={() => navigate("/ingest/documents")}>
							返回文档列表
						</Button>
						<Typography.Text type="secondary">
							DELETE /api/v1/documents/{"{documentId}"}
						</Typography.Text>
					</Space>
				}
			>
				<Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
					删除操作会清理整个 document 资产。确认前需要输入完整
					documentId。
				</Typography.Paragraph>

				<Form
					form={form}
					layout="inline"
					initialValues={{
						documentId: initialDocumentId,
					}}
				>
					<Form.Item name="documentId" style={{ flex: 1, minWidth: 320 }}>
						<Input
							aria-label="输入 documentId"
							autoComplete="off"
							spellCheck={false}
							placeholder="输入 documentId"
							allowClear
						/>
					</Form.Item>
					<Button
						danger
						type="primary"
						loading={deleteMutation.isPending}
						onClick={openDeleteConfirm}
					>
						删除文档
					</Button>
				</Form>
			</Card>

			{deleteMutation.isError && <ApiErrorAlert error={deleteMutation.error} />}
			{conflictWarning}

			<DeleteDocumentConfirmModal
				open={Boolean(confirmDocumentId)}
				document={confirmDocumentId ? { documentId: confirmDocumentId } : null}
				confirmLoading={deleteMutation.isPending}
				error={deleteMutation.error}
				onCancel={() => setConfirmDocumentId(null)}
				onConfirm={() => {
					if (confirmDocumentId) {
						deleteMutation.mutate(confirmDocumentId);
					}
				}}
			/>
		</Space>
	);
}
