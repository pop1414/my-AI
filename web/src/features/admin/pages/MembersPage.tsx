import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	Button,
	Form,
	Modal,
	Select,
	Space,
	Table,
	Tag,
	Typography,
	message,
} from "antd";
import { WorkspaceRoleTag } from "../../../shared/ui/WorkspaceRoleTag";
import type { ColumnsType } from "antd/es/table";
import { useNavigate } from "react-router-dom";
import {
	listMembers,
	updateMemberRole,
	type WorkspaceMember,
} from "../../../shared/api/adminApi";
import { useAuth } from "../../../shared/auth/AuthContext";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import type { ApiError } from "../../../shared/api/request";

const { Title } = Typography;

const roleOptions: {
	value: WorkspaceMember["workspaceRole"];
	label: string;
}[] = [
	{ value: "WORKSPACE_OWNER", label: "WORKSPACE_OWNER" },
	{ value: "WORKSPACE_ADMIN", label: "WORKSPACE_ADMIN" },
	{ value: "WORKSPACE_MEMBER", label: "WORKSPACE_MEMBER" },
];

export function MembersPage() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { user } = useAuth();
	const [editingMember, setEditingMember] = useState<WorkspaceMember | null>(
		null,
	);
	const [roleForm] = Form.useForm<{
		workspaceRole: WorkspaceMember["workspaceRole"];
	}>();

	const membersQuery = useQuery({
		queryKey: ["admin", "members"],
		queryFn: listMembers,
	});

	const roleMutation = useMutation({
		mutationFn: (params: {
			userId: string;
			workspaceRole: WorkspaceMember["workspaceRole"];
		}) => updateMemberRole(params.userId, params.workspaceRole),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["admin", "members"] });
			setEditingMember(null);
			roleForm.resetFields();
			message.success("成员角色已更新");
		},
	});

	const currentRole = user?.workspaceRole;
	const canEditRole = (record: WorkspaceMember) => {
		if (record.workspaceRole === "WORKSPACE_OWNER") {
			return false;
		}
		if (currentRole === "WORKSPACE_OWNER") {
			return true;
		}
		return record.workspaceRole === "WORKSPACE_MEMBER";
	};

	const canConfigureGrants = (record: WorkspaceMember) =>
		record.workspaceRole === "WORKSPACE_MEMBER";

	const columns: ColumnsType<WorkspaceMember> = [
		{ title: "用户名", dataIndex: "username", width: 160 },
		{ title: "显示名", dataIndex: "displayName", width: 160 },
		{
			title: "工作区角色",
			dataIndex: "workspaceRole",
			width: 180,
			render: (value: string) => <WorkspaceRoleTag role={value} />,
		},
		{
			title: "成员状态",
			dataIndex: "membershipStatus",
			width: 120,
			render: (value: string) => (
				<Tag className="console-pill console-pill--blue">{value}</Tag>
			),
		},
		{
			title: "操作",
			key: "action",
			width: 220,
			render: (_, record) => (
				<Space size={8}>
					<Button
						size="small"
						className="console-action-btn"
						disabled={!canEditRole(record)}
						onClick={() => {
							setEditingMember(record);
							roleForm.setFieldsValue({
								workspaceRole: record.workspaceRole,
							});
						}}
					>
						编辑角色
					</Button>
					<Button
						size="small"
						className="console-action-btn"
						disabled={!canConfigureGrants(record)}
						onClick={() =>
							navigate(
								`/admin/members/${encodeURIComponent(record.userId)}/grants?tab=knowledge`,
							)
						}
					>
						授权配置
					</Button>
				</Space>
			),
		},
	];

	const apiError = membersQuery.error as ApiError | null;

	return (
		<div>
			<Title level={4} style={{ marginBottom: 16 }}>
				成员管理
			</Title>

			{apiError && (
				<div style={{ marginBottom: 16 }}>
					<ApiErrorAlert error={apiError} />
				</div>
			)}

			<Table<WorkspaceMember>
				columns={columns}
				dataSource={membersQuery.data ?? []}
				rowKey="userId"
				loading={membersQuery.isLoading}
				pagination={false}
				locale={{ emptyText: "暂无成员数据" }}
			/>

			<Modal
				title="编辑成员角色"
				open={editingMember !== null}
				onOk={() => roleForm.submit()}
				onCancel={() => {
					setEditingMember(null);
					roleForm.resetFields();
				}}
				confirmLoading={roleMutation.isPending}
				destroyOnClose
			>
				<Form
					form={roleForm}
					layout="vertical"
					onFinish={(values) => {
						if (editingMember) {
							roleMutation.mutate({
								userId: editingMember.userId,
								workspaceRole: values.workspaceRole,
							});
						}
					}}
				>
					<Form.Item label="成员">
						<span>
							{editingMember?.displayName ??
								editingMember?.username ??
								""}
						</span>
					</Form.Item>
					<Form.Item
						name="workspaceRole"
						label="工作区角色"
						rules={[{ required: true, message: "请选择角色" }]}
					>
						<Select options={roleOptions} />
					</Form.Item>
					{roleMutation.error && (
						<Form.Item>
							<ApiErrorAlert error={roleMutation.error} />
						</Form.Item>
					)}
				</Form>
			</Modal>
		</div>
	);
}
