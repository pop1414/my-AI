import { useState } from "react";
import { Alert, Descriptions, Input, Modal, Space, Typography } from "antd";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { KnowledgeBaseStatusTag } from "./KnowledgeBaseStatusTag";
import type { KnowledgeBase } from "../../../shared/api/knowledgeApi";

export function DeleteKnowledgeBaseConfirmModal({
	open,
	knowledgeBase,
	confirmLoading,
	error,
	onCancel,
	onConfirm,
}: {
	open: boolean;
	knowledgeBase: KnowledgeBase | null;
	confirmLoading: boolean;
	error?: unknown;
	onCancel: () => void;
	onConfirm: () => void;
}) {
	if (!open) {
		return null;
	}

	return (
		<DeleteKnowledgeBaseConfirmDialog
			key={knowledgeBase?.id ?? "empty"}
			knowledgeBase={knowledgeBase}
			confirmLoading={confirmLoading}
			error={error}
			onCancel={onCancel}
			onConfirm={onConfirm}
		/>
	);
}

function DeleteKnowledgeBaseConfirmDialog({
	knowledgeBase,
	confirmLoading,
	error,
	onCancel,
	onConfirm,
}: {
	knowledgeBase: KnowledgeBase | null;
	confirmLoading: boolean;
	error?: unknown;
	onCancel: () => void;
	onConfirm: () => void;
}) {
	const [confirmationText, setConfirmationText] = useState("");
	const expectedKbId = knowledgeBase?.id ?? "";
	const confirmDisabled =
		expectedKbId.length === 0 || confirmationText !== expectedKbId;

	return (
		<Modal
			title="确认删除知识库"
			open
			okText="确认删除知识库"
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
					message="该操作会软删除知识库"
					description="删除后，知识库将以删除态保留在治理列表中，不再参与问答、文档上传和配置操作。相关文档、授权和审计记录将保留用于追溯。"
				/>
				{knowledgeBase && (
					<Descriptions column={1} size="small" bordered>
						<Descriptions.Item label="知识库名称">
							<Typography.Text strong>{knowledgeBase.name}</Typography.Text>
						</Descriptions.Item>
						<Descriptions.Item label="知识库 ID">
							<Typography.Text copyable={{ text: knowledgeBase.id }}>
								{knowledgeBase.id}
							</Typography.Text>
						</Descriptions.Item>
						<Descriptions.Item label="当前状态">
							<KnowledgeBaseStatusTag status={knowledgeBase.status} />
						</Descriptions.Item>
						<Descriptions.Item label="已索引文档数">
							{knowledgeBase.indexedDocumentCount}
						</Descriptions.Item>
					</Descriptions>
				)}
				<div style={{ marginTop: 8 }}>
					<Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
						输入完整知识库 ID 后确认删除
					</Typography.Text>
					<Input
						aria-label="输入完整知识库 ID 确认删除"
						autoComplete="off"
						spellCheck={false}
						value={confirmationText}
						placeholder={expectedKbId}
						status={
							confirmationText.length > 0 && confirmDisabled ? "error" : ""
						}
						onChange={(event) => setConfirmationText(event.target.value)}
					/>
				</div>
				{Boolean(error) && <ApiErrorAlert error={error} />}
			</Space>
		</Modal>
	);
}
