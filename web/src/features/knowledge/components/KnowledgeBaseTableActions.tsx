import { Button, Space } from "antd";
import { ConsoleLinkButton } from "../../../shared/ui/ConsoleLinkButton";
import type { KnowledgeBase } from "../../../shared/api/knowledgeApi";

interface KnowledgeBaseTableActionsProps {
	record: KnowledgeBase;
	canManageKnowledgeBases: boolean;
	onEdit: (record: KnowledgeBase) => void;
}

export function KnowledgeBaseTableActions({
	record,
	canManageKnowledgeBases,
	onEdit,
}: KnowledgeBaseTableActionsProps) {
	return (
		<Space size={8}>
			{canManageKnowledgeBases && (
				<Button
					size="small"
					className="console-action-button"
					onClick={() => onEdit(record)}
				>
					编辑
				</Button>
			)}
			<ConsoleLinkButton
				to={`/qa?kbId=${encodeURIComponent(record.id)}`}
				variant="primary"
				size="small"
				disabled={record.status !== "ACTIVE"}
				onClick={() => {
					localStorage.setItem("myai:lastKbId", record.id);
				}}
			>
				去问答
			</ConsoleLinkButton>
			{canManageKnowledgeBases && (
				<ConsoleLinkButton
					variant="default"
					size="small"
					to={`/admin/knowledge-bases/${encodeURIComponent(record.id)}/grants`}
				>
					授权管理
				</ConsoleLinkButton>
			)}
		</Space>
	);
}
