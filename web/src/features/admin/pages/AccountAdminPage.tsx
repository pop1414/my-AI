import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	Button,
	Form,
	Input,
	Modal,
	Popconfirm,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import {
	createManagedAccount,
	listManagedAccounts,
	removeManagedAccountMembership,
	resetManagedAccountPassword,
	updateManagedAccountStatus,
	type ManagedAccount,
} from "../../../shared/api/adminApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

const { Title } = Typography;

const roleOptions: {
	value: ManagedAccount["workspaceRole"];
	label: string;
}[] = [
	{ value: "WORKSPACE_OWNER", label: "WORKSPACE_OWNER" },
	{ value: "WORKSPACE_ADMIN", label: "WORKSPACE_ADMIN" },
	{ value: "WORKSPACE_MEMBER", label: "WORKSPACE_MEMBER" },
];

function formatTime(iso?: string | null): string {
	if (!iso) {
		return "-";
	}
	try {
		return new Date(iso).toLocaleString("zh-CN", {
			year: "numeric",
			month: "2-digit",
			day: "2-digit",
			hour: "2-digit",
			minute: "2-digit",
			second: "2-digit",
		});
	} catch {
		return iso;
	}
}

export function AccountAdminPage() {
	const queryClient = useQueryClient();
	const [createModalOpen, setCreateModalOpen] = useState(false);
	const [passwordModalAccount, setPasswordModalAccount] =
		useState<ManagedAccount | null>(null);
	const [createForm] = Form.useForm<{
		username: string;
		displayName: string;
		password: string;
		workspaceRole: ManagedAccount["workspaceRole"];
	}>();
	const [passwordForm] = Form.useForm<{ password: string }>();

	const accountsQuery = useQuery({
		queryKey: ["admin", "accounts"],
		queryFn: listManagedAccounts,
	});

	const createMutation = useMutation({
		mutationFn: createManagedAccount,
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["admin", "accounts"] });
			setCreateModalOpen(false);
			createForm.resetFields();
		},
	});

	const statusMutation = useMutation({
		mutationFn: (params: {
			userId: string;
			userStatus: ManagedAccount["userStatus"];
		}) => updateManagedAccountStatus(params.userId, params.userStatus),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["admin", "accounts"] });
		},
	});

	const passwordMutation = useMutation({
		mutationFn: (params: { userId: string; password: string }) =>
			resetManagedAccountPassword(params.userId, params.password),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["admin", "accounts"] });
			setPasswordModalAccount(null);
			passwordForm.resetFields();
		},
	});

	const removeMutation = useMutation({
		mutationFn: (userId: string) => removeManagedAccountMembership(userId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["admin", "accounts"] });
		},
	});

	const columns: ColumnsType<ManagedAccount> = [
		{ title: "用户名", dataIndex: "username", width: 160 },
		{ title: "显示名", dataIndex: "displayName", width: 160 },
		{
			title: "账号状态",
			dataIndex: "userStatus",
			width: 120,
			render: (value: ManagedAccount["userStatus"]) => (
				<Tag color={value === "ACTIVE" ? "success" : "default"}>
					{value}
				</Tag>
			),
		},
		{
			title: "工作区角色",
			dataIndex: "workspaceRole",
			width: 180,
			render: (value: ManagedAccount["workspaceRole"]) => (
				<Tag>{value}</Tag>
			),
		},
		{
			title: "成员状态",
			dataIndex: "membershipStatus",
			width: 120,
			render: (value: ManagedAccount["membershipStatus"]) => (
				<Tag color={value === "ACTIVE" ? "success" : "default"}>
					{value}
				</Tag>
			),
		},
		{
			title: "失败次数",
			dataIndex: "failedLoginCount",
			width: 100,
		},
		{
			title: "锁定截止时间",
			dataIndex: "lockedUntil",
			width: 180,
			render: (value: string | null | undefined) => formatTime(value),
		},
		{
			title: "操作",
			key: "action",
			width: 280,
			render: (_, record) => (
				<Space size="small" wrap>
					<Button
						size="small"
						onClick={() =>
							statusMutation.mutate({
								userId: record.userId,
								userStatus:
									record.userStatus === "ACTIVE"
										? "DISABLED"
										: "ACTIVE",
							})
						}
						loading={
							statusMutation.isPending &&
							statusMutation.variables?.userId === record.userId
						}
					>
						{record.userStatus === "ACTIVE" ? "停用账号" : "启用账号"}
					</Button>
					<Button
						size="small"
						onClick={() => {
							setPasswordModalAccount(record);
							passwordForm.resetFields();
						}}
					>
						重置密码
					</Button>
					<Popconfirm
						title="确认移除成员关系？"
						description={`将移除 ${record.displayName || record.username} 的工作区成员关系`}
						okText="确认移除"
						cancelText="取消"
						disabled={record.membershipStatus !== "ACTIVE"}
						onConfirm={() => removeMutation.mutate(record.userId)}
					>
						<Button
							size="small"
							danger
							disabled={record.membershipStatus !== "ACTIVE"}
						>
							移除成员
						</Button>
					</Popconfirm>
				</Space>
			),
		},
	];

	return (
		<div>
			<div
				style={{
					display: "flex",
					justifyContent: "space-between",
					alignItems: "center",
					marginBottom: 16,
				}}
			>
				<Title level={4} style={{ margin: 0 }}>
					账号管理
				</Title>
				<Button
					type="primary"
					icon={<PlusOutlined />}
					onClick={() => {
						createForm.resetFields();
						setCreateModalOpen(true);
					}}
				>
					新增账号
				</Button>
			</div>

			{accountsQuery.isError && (
				<div style={{ marginBottom: 16 }}>
					<ApiErrorAlert error={accountsQuery.error} />
				</div>
			)}
			{statusMutation.isError && (
				<div style={{ marginBottom: 16 }}>
					<ApiErrorAlert error={statusMutation.error} />
				</div>
			)}
			{removeMutation.isError && (
				<div style={{ marginBottom: 16 }}>
					<ApiErrorAlert error={removeMutation.error} />
				</div>
			)}

			<Table<ManagedAccount>
				columns={columns}
				dataSource={accountsQuery.data ?? []}
				rowKey="userId"
				loading={accountsQuery.isLoading}
				pagination={false}
				locale={{ emptyText: "暂无账号数据" }}
			/>

			<Modal
				title="新增账号"
				open={createModalOpen}
				onOk={() => createForm.submit()}
				onCancel={() => {
					setCreateModalOpen(false);
					createForm.resetFields();
				}}
				confirmLoading={createMutation.isPending}
				destroyOnClose
			>
				<Form
					form={createForm}
					layout="vertical"
					initialValues={{ workspaceRole: "WORKSPACE_MEMBER" }}
					onFinish={(values) => createMutation.mutate(values)}
				>
					<Form.Item
						name="username"
						label="用户名"
						rules={[{ required: true, message: "请输入用户名" }]}
					>
						<Input />
					</Form.Item>
					<Form.Item
						name="displayName"
						label="显示名"
						rules={[{ required: true, message: "请输入显示名" }]}
					>
						<Input />
					</Form.Item>
					<Form.Item
						name="password"
						label="初始密码"
						rules={[{ required: true, message: "请输入初始密码" }]}
					>
						<Input.Password />
					</Form.Item>
					<Form.Item
						name="workspaceRole"
						label="工作区角色"
						rules={[{ required: true, message: "请选择工作区角色" }]}
					>
						<Select options={roleOptions} />
					</Form.Item>
					{createMutation.isError && (
						<Form.Item>
							<ApiErrorAlert error={createMutation.error} />
						</Form.Item>
					)}
				</Form>
			</Modal>

			<Modal
				title="重置密码"
				open={passwordModalAccount !== null}
				onOk={() => passwordForm.submit()}
				onCancel={() => {
					setPasswordModalAccount(null);
					passwordForm.resetFields();
				}}
				confirmLoading={passwordMutation.isPending}
				destroyOnClose
			>
				<Form
					form={passwordForm}
					layout="vertical"
					onFinish={(values) => {
						if (passwordModalAccount) {
							passwordMutation.mutate({
								userId: passwordModalAccount.userId,
								password: values.password,
							});
						}
					}}
				>
					<Form.Item label="目标账号">
						<span>
							{passwordModalAccount?.displayName ??
								passwordModalAccount?.username ??
								""}
						</span>
					</Form.Item>
					<Form.Item
						name="password"
						label="新密码"
						rules={[{ required: true, message: "请输入新密码" }]}
					>
						<Input.Password />
					</Form.Item>
					{passwordMutation.isError && (
						<Form.Item>
							<ApiErrorAlert error={passwordMutation.error} />
						</Form.Item>
					)}
				</Form>
			</Modal>
		</div>
	);
}
