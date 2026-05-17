import { Tag } from "antd";
import type { WorkspaceMember } from "../api/adminApi";

interface WorkspaceRoleTagProps {
	role?: WorkspaceMember["workspaceRole"] | string;
	className?: string;
}

const roleConfig: Record<string, { label: string; color: string; className: string }> = {
	WORKSPACE_OWNER: {
		label: "Owner",
		color: "var(--console-accent)",
		className: "console-role-tag--owner",
	},
	WORKSPACE_ADMIN: {
		label: "Admin",
		color: "var(--console-timeline-read)",
		className: "console-role-tag--admin",
	},
	WORKSPACE_MEMBER: {
		label: "Member",
		color: "var(--console-ink-faint)",
		className: "console-role-tag--member",
	},
};

export function WorkspaceRoleTag({ role, className }: WorkspaceRoleTagProps) {
	const config = role ? roleConfig[role] : null;
	
	if (!config) {
		return <Tag bordered={false}>{role}</Tag>;
	}

	return (
		<Tag
			bordered={false}
			className={`console-role-tag ${config.className} ${className || ""}`}
			style={{
				backgroundColor: `${config.color}15`, // 15% opacity background
				color: config.color === "var(--console-accent)" ? "var(--console-accent-strong)" : config.color,
				fontWeight: 600,
				fontSize: 11,
				textTransform: "uppercase",
				letterSpacing: "0.05em",
				borderRadius: 4,
				padding: "0 8px",
				border: `1px solid ${config.color}30`, // 30% opacity border
			}}
		>
			{config.label}
		</Tag>
	);
}
