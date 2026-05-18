import { Tag } from "antd";
import type { KnowledgeBase } from "../../../shared/api/knowledgeApi";

interface KnowledgeBaseStatusTagProps {
	status: KnowledgeBase["status"];
}

export function KnowledgeBaseStatusTag({ status }: KnowledgeBaseStatusTagProps) {
	const isActive = status === "ACTIVE";
	return (
		<Tag
			bordered={false}
			color={isActive ? "success" : "default"}
			style={{ 
				borderRadius: 4, 
				fontWeight: 600,
				fontSize: '11px',
				padding: '0 8px',
				textTransform: 'uppercase'
			}}
		>
			{isActive ? "ACTIVE" : "已停用"}
		</Tag>
	);
}
