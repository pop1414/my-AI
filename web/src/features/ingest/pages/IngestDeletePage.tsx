import { useState, useMemo } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { Card, Space, Typography, Form, Input, Button, Alert } from "antd";
import { z } from "zod";
import { deleteDocument, getDocumentStatus } from "../../../shared/api/ingestApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { ApiError } from "../../../shared/api/request";
import { DeleteDocumentConfirmModal } from "./DeleteDocumentConfirmModal";

const { Title, Text } = Typography;

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

	const docStatusQuery = useQuery({
		queryKey: ["document-status", urlDocumentId || initialDocumentId],
		queryFn: () => getDocumentStatus(urlDocumentId || initialDocumentId),
		enabled: !!(urlDocumentId || initialDocumentId),
	});

  const kbQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});

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
							删除文档 · {docStatusQuery.data?.latestFilename || urlDocumentId || initialDocumentId}
						</Title>
						<Text type="secondary" style={{ fontSize: 12, fontFamily: 'var(--console-font-mono)' }}>
							ID: {urlDocumentId || initialDocumentId}
						</Text>
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
							返回文档列表
						</Button>
						<Typography.Text type="secondary">
							DELETE /api/v1/documents/{"{documentId}"}
						</Typography.Text>
					</Space>
				}
			>
				<Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
					删除操作会清理整个 document 资产及其所有版本。确认前需要输入完整
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
			{docStatusQuery.isError && <ApiErrorAlert error={docStatusQuery.error} />}
			{kbQuery.isError && <ApiErrorAlert error={kbQuery.error} />}
			{conflictWarning}

			<DeleteDocumentConfirmModal
				open={Boolean(confirmDocumentId)}
				document={confirmDocumentId ? { 
					documentId: confirmDocumentId,
					filename: docStatusQuery.data?.latestFilename,
					status: docStatusQuery.data?.status,
					latestVersionNumber: docStatusQuery.data?.latestVersionNumber
				} : null}
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
