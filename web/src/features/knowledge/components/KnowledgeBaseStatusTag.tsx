import { Tag } from "antd";
import type { KnowledgeBase } from "../../../shared/api/knowledgeApi";

interface KnowledgeBaseStatusTagProps {
	status: KnowledgeBase["status"];
}

export function KnowledgeBaseStatusTag({ status }: KnowledgeBaseStatusTagProps) {
	const isActive = status === "ACTIVE";
	const isDeleted = status === "DELETED";

	let color: string = "default";
	let label: string = status;

	if (isActive) {
		color = "success";
		label = "ACTIVE";
	} else if (isDeleted) {
		color = "error";
		label = "已删除";
	} else if (status === "INACTIVE") {
		label = "已停用";
	}

	return (
		<Tag
			bordered={false}
			color={color}
			style={{ 
				borderRadius: 4, 
				fontWeight: 600,
				fontSize: '11px',
				padding: '0 8px',
				textTransform: 'uppercase'
			}}
		>
			{label}
		</Tag>
	);
}

