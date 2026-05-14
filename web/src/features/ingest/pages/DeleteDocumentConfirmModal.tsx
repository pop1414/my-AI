import { useState } from "react";
import { Alert, Descriptions, Input, Modal, Space, Tag, Typography } from "antd";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

export interface DeleteDocumentSummary {
	documentId: string;
	filename?: string;
	status?: string;
	latestVersionNumber?: number;
	latestVersionOriginType?: string;
}

function statusColor(status?: string): string {
	switch (status) {
		case "UPLOADED":
		case "ACCEPTED":
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

export function DeleteDocumentConfirmModal({
	open,
	document,
	confirmLoading,
	error,
	onCancel,
	onConfirm,
}: {
	open: boolean;
	document: DeleteDocumentSummary | null;
	confirmLoading: boolean;
	error?: unknown;
	onCancel: () => void;
	onConfirm: () => void;
}) {
	if (!open) {
		return null;
	}

	return (
		<DeleteDocumentConfirmDialog
			key={document?.documentId ?? "empty"}
			document={document}
			confirmLoading={confirmLoading}
			error={error}
			onCancel={onCancel}
			onConfirm={onConfirm}
		/>
	);
}

function DeleteDocumentConfirmDialog({
	document,
	confirmLoading,
	error,
	onCancel,
	onConfirm,
}: {
	document: DeleteDocumentSummary | null;
	confirmLoading: boolean;
	error?: unknown;
	onCancel: () => void;
	onConfirm: () => void;
}) {
	const [confirmationText, setConfirmationText] = useState("");
	const expectedDocumentId = document?.documentId ?? "";
	const confirmDisabled =
		expectedDocumentId.length === 0 || confirmationText !== expectedDocumentId;

	return (
		<Modal
			title="确认删除 document 资产"
			open
			okText="确认删除整个 document"
			cancelText="取消"
			okButtonProps={{ danger: true, disabled: confirmDisabled }}
			confirmLoading={confirmLoading}
			onOk={onConfirm}
			onCancel={onCancel}
			destroyOnHidden
		>
			<Space direction="vertical" size={14} style={{ width: "100%" }}>
				<Alert
					type="warning"
					showIcon
					message="该操作会删除整个 document 资产"
					description="删除后，同内容重新上传会生成新的 documentId，新文档不会继承旧文档级授权；如需继续使用，请重新上传并重新配置授权。"
				/>
				{document && (
					<Descriptions column={1} size="small" bordered>
						<Descriptions.Item label="documentId">
							<Typography.Text copyable={{ text: document.documentId }}>
								{document.documentId}
							</Typography.Text>
						</Descriptions.Item>
						{document.filename && (
							<Descriptions.Item label="当前最新文件名">
								{document.filename}
							</Descriptions.Item>
						)}
						{document.latestVersionNumber && (
							<Descriptions.Item label="当前最新版本">
								v{document.latestVersionNumber}
								{document.latestVersionOriginType
									? ` · ${document.latestVersionOriginType}`
									: ""}
							</Descriptions.Item>
						)}
						{document.status && (
							<Descriptions.Item label="当前最新状态">
								<Tag color={statusColor(document.status)}>{document.status}</Tag>
							</Descriptions.Item>
						)}
					</Descriptions>
				)}
				<label>
					<Typography.Text strong>输入完整 documentId 后确认删除</Typography.Text>
					<Input
						aria-label="输入完整 documentId 确认删除"
						autoComplete="off"
						spellCheck={false}
						value={confirmationText}
						placeholder={expectedDocumentId}
						status={
							confirmationText.length > 0 && confirmDisabled ? "error" : ""
						}
						onChange={(event) => setConfirmationText(event.target.value)}
					/>
				</label>
				{Boolean(error) && <ApiErrorAlert error={error} />}
			</Space>
		</Modal>
	);
}
