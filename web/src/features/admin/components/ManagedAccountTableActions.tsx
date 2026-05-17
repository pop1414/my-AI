import { Button, Popconfirm, Space } from "antd";
import type { ManagedAccount } from "../../../shared/api/adminApi";

interface ManagedAccountTableActionsProps {
	record: ManagedAccount;
	canManage: boolean;
	statusPending: boolean;
	onStatusUpdate: (record: ManagedAccount) => void;
	onPasswordReset: (record: ManagedAccount) => void;
	onMemberRemove: (userId: string) => void;
}

export function ManagedAccountTableActions({
	record,
	canManage,
	statusPending,
	onStatusUpdate,
	onPasswordReset,
	onMemberRemove,
}: ManagedAccountTableActionsProps) {
	const isUserActive = record.userStatus === "ACTIVE";
	const isMemberActive = record.membershipStatus === "ACTIVE";

	return (
		<Space size={8} wrap>
			<Button
				size="small"
				danger={isUserActive}
				disabled={!canManage}
				onClick={() => onStatusUpdate(record)}
				loading={statusPending}
			>
				{isUserActive ? "停用账号" : "启用账号"}
			</Button>
			<Button
				size="small"
				disabled={!canManage}
				onClick={() => onPasswordReset(record)}
			>
				重置密码
			</Button>
			<Popconfirm
				title="确认移除成员关系？"
				description={`将移除 ${record.displayName || record.username} 的工作区成员关系`}
				okText="确认移除"
				cancelText="取消"
				disabled={!isMemberActive || !canManage}
				onConfirm={() => onMemberRemove(record.userId)}
			>
				<Button
					size="small"
					danger
					disabled={!isMemberActive || !canManage}
				>
					移除成员
				</Button>
			</Popconfirm>
		</Space>
	);
}
