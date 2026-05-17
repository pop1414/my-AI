import { Button, Space } from "antd";
import { ConsoleLinkButton } from "../../../shared/ui/ConsoleLinkButton";
import type { KnowledgeBase } from "../../../shared/api/knowledgeApi";
import { EditOutlined, MessageOutlined, SafetyCertificateOutlined } from "@ant-design/icons";

interface KnowledgeBaseTableActionsProps {
	record: KnowledgeBase;
	onEdit: (record: KnowledgeBase) => void;
}

export function KnowledgeBaseTableActions({
	record,
	onEdit,
}: KnowledgeBaseTableActionsProps) {
	return (
		<Space size={8}>
			<Button
				size="small"
				className="console-action-button"
				onClick={() => onEdit(record)}
			>
				<Space size={4}><EditOutlined />配置</Space>
			</Button>
			
			<ConsoleLinkButton
				variant="default"
				size="small"
				to={`/admin/knowledge-bases/${encodeURIComponent(record.id)}/grants`}
			>
				<Space size={4}><SafetyCertificateOutlined />授权</Space>
			</ConsoleLinkButton>

			<ConsoleLinkButton
				to={`/qa?kbId=${encodeURIComponent(record.id)}`}
				variant="primary"
				size="small"
				disabled={record.status !== "ACTIVE"}
				onClick={() => {
					localStorage.setItem("myai:lastKbId", record.id);
				}}
			>
				<Space size={4}><MessageOutlined />问答</Space>
			</ConsoleLinkButton>
		</Space>
	);
}
