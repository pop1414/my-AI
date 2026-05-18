import { Button, Card, Typography } from "antd";
import { useNavigate } from "react-router-dom";
import type { WorkspaceMember } from "../../../shared/api/adminApi";

interface MemberPageHeaderProps {
	userId: string;
	member?: WorkspaceMember;
	title: string;
}

export function MemberPageHeader({ userId, member, title }: MemberPageHeaderProps) {
	const navigate = useNavigate();

	return (
		<Card className="console-content-header" style={{ marginBottom: 16 }}>
			<div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
				<div>
					<Typography.Title level={4} style={{ margin: 0 }}>
						{title}
					</Typography.Title>
					<Typography.Paragraph type="secondary" style={{ marginBottom: 0, marginTop: 4 }}>
						{member ? (
							<>
								<span style={{ fontWeight: 500, color: 'var(--console-ink)' }}>{member.displayName}</span>
								<Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12, fontFamily: 'var(--console-font-mono)' }}>
									@{member.username}
								</Typography.Text>
							</>
						) : (
							<Typography.Text code>{userId}</Typography.Text>
						)}
					</Typography.Paragraph>
				</div>
				<Button
					onClick={() => navigate("/admin?tab=members")}
				>
					返回成员管理
				</Button>
			</div>
		</Card>
	);
}
