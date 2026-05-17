import { Tag } from "antd";
import { statusColor } from "../utils/formatters";

interface DocumentStatusTagProps {
	status: string;
}

export function DocumentStatusTag({ status }: DocumentStatusTagProps) {
	return (
		<Tag bordered={false} color={statusColor(status)} style={{ borderRadius: 4 }}>
			{status}
		</Tag>
	);
}
