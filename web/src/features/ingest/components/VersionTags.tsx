import { Space, Tag } from "antd";
import { statusColor } from "../utils/formatters";
import type { DocumentVersionHistoryItem } from "../../../shared/api/ingestApi";

interface VersionTagsProps {
	version: DocumentVersionHistoryItem;
	isActive: boolean;
	hasBeenRolledBackAsLatest: boolean;
}

export function VersionTags({
	version,
	isActive,
	hasBeenRolledBackAsLatest,
}: VersionTagsProps) {
	return (
		<Space size={[6, 6]} wrap>
			{version.isLatestVersion && <Tag color="gold">最新版本</Tag>}
			{isActive && <Tag color="blue">当前查看</Tag>}
			{version.isAskableVersion && <Tag color="green">当前问答基线</Tag>}
			{version.versionOriginType === "ROLLBACK" && (
				<Tag color="orange">回退产生</Tag>
			)}
			{hasBeenRolledBackAsLatest && <Tag>曾回退为最新版本</Tag>}
			<Tag color={statusColor(version.status)}>{version.status}</Tag>
		</Space>
	);
}
