import { Tag } from "antd";
import type { ManagedAccount } from "../../../shared/api/adminApi";

interface AccountStatusTagsProps {
	userStatus: ManagedAccount["userStatus"];
	membershipStatus: ManagedAccount["membershipStatus"];
}

export function AccountStatusTags({
	userStatus,
	membershipStatus,
}: AccountStatusTagsProps) {
	const isUserActive = userStatus === "ACTIVE";
	const isMemberActive = membershipStatus === "ACTIVE";

	return (
		<>
			<Tag
				bordered={false}
				color={isUserActive ? "success" : "default"}
				style={{ borderRadius: 4, fontWeight: 500 }}
			>
				{userStatus}
			</Tag>
			<Tag
				bordered={false}
				color={isMemberActive ? "blue" : "warning"}
				style={{ borderRadius: 4, fontWeight: 500 }}
			>
				{membershipStatus}
			</Tag>
		</>
	);
}
