import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	Button,
	Checkbox,
	Form,
	Input,
	Modal,
	Popconfirm,
	Select,
	Space,
	Steps,
	Table,
	Tag,
	Typography,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { useNavigate } from "react-router-dom";
import {
	createManagedAccount,
	createManagedMember,
	listManagedAccounts,
	removeManagedAccountMembership,
	resetManagedAccountPassword,
	updateManagedAccountStatus,
	type ManagedAccount,
} from "../../../shared/api/adminApi";
import {
	listKnowledgeBases,
	type KnowledgeBase,
} from "../../../shared/api/knowledgeApi";
import { useAuth } from "../../../shared/auth/AuthContext";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

const { Title } = Typography;

const knowledgeRoleOptions = [
	{ value: "KB_MANAGER", label: "管理者" },
	{ value: "KB_CONTRIBUTOR", label: "贡献者" },
	{ value: "KB_READER", label: "读者" },
	{ value: "KB_ASKER", label: "问答者" },
] as const;

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
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { user } = useAuth();
	const [createAdminModalOpen, setCreateAdminModalOpen] = useState(false);
	const [createMemberModalOpen, setCreateMemberModalOpen] = useState(false);
	const [memberProvisionStep, setMemberProvisionStep] = useState(0);
	const [memberDraft, setMemberDraft] = useState<{
		username: string;
		displayName: string;
		password: string;
	} | null>(null);
	const [selectedKnowledgeBaseIds, setSelectedKnowledgeBaseIds] = useState<string[]>(
		[],
	);
	const [knowledgeBaseRoles, setKnowledgeBaseRoles] = useState<
		Record<string, (typeof knowledgeRoleOptions)[number]["value"]>
	>({});
	const [passwordModalAccount, setPasswordModalAccount] =
		useState<ManagedAccount | null>(null);
	const [adminForm] = Form.useForm<{
		username: string;
		displayName: string;
		password: string;
	}>();
	const [memberForm] = Form.useForm<{
		username: string;
		displayName: string;
		password: string;
	}>();
	const [passwordForm] = Form.useForm<{ password: string }>();

	const accountsQuery = useQuery({
		queryKey: ["admin", "accounts"],
		queryFn: listManagedAccounts,
	});
	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});

	const createAdminMutation = useMutation({
		mutationFn: createManagedAccount,
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["admin", "accounts"] });
			setCreateAdminModalOpen(false);
			adminForm.resetFields();
		},
	});

	const createMemberMutation = useMutation({
		mutationFn: createManagedMember,
		onSuccess: (created) => {
			queryClient.invalidateQueries({ queryKey: ["admin", "accounts"] });
			queryClient.invalidateQueries({ queryKey: ["admin", "members"] });
			setCreateMemberModalOpen(false);
			resetMemberProvisionWizard();
			navigate(
				`/admin/members/${encodeURIComponent(created.userId)}/grants?tab=knowledge`,
			);
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

	const currentRole = user?.workspaceRole;
	const canCreateAdmin = currentRole === "WORKSPACE_OWNER";
	const canManageAccount = (record: ManagedAccount) => {
		if (record.workspaceRole === "WORKSPACE_OWNER") {
			return false;
		}
		if (currentRole === "WORKSPACE_OWNER") {
			return true;
		}
		return record.workspaceRole === "WORKSPACE_MEMBER";
	};

	const activeKnowledgeBases = (knowledgeQuery.data ?? []).filter(
		(item) => item.status === "ACTIVE",
	);

	const resetMemberProvisionWizard = () => {
		memberForm.resetFields();
		setMemberDraft(null);
		setSelectedKnowledgeBaseIds([]);
		setKnowledgeBaseRoles({});
		setMemberProvisionStep(0);
	};

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
			render: (value: ManagedAccount["workspaceRole"]) => <Tag>{value}</Tag>,
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
			width: 320,
			render: (_, record) => (
				<Space size="small" wrap>
					<Button
						size="small"
						disabled={!canManageAccount(record)}
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
						disabled={!canManageAccount(record)}
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
						disabled={
							record.membershipStatus !== "ACTIVE" ||
							!canManageAccount(record)
						}
						onConfirm={() => removeMutation.mutate(record.userId)}
					>
						<Button
							size="small"
							danger
							disabled={
								record.membershipStatus !== "ACTIVE" ||
								!canManageAccount(record)
							}
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
				<Space>
					<Button
						type="primary"
						icon={<PlusOutlined />}
						onClick={() => {
							resetMemberProvisionWizard();
							setCreateMemberModalOpen(true);
						}}
					>
						新增成员
					</Button>
					{canCreateAdmin && (
						<Button
							icon={<PlusOutlined />}
							onClick={() => {
								adminForm.resetFields();
								setCreateAdminModalOpen(true);
							}}
						>
							新增管理员
						</Button>
					)}
				</Space>
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
				title="新增管理员"
				open={createAdminModalOpen}
				onOk={() => adminForm.submit()}
				onCancel={() => {
					setCreateAdminModalOpen(false);
					adminForm.resetFields();
				}}
				confirmLoading={createAdminMutation.isPending}
				destroyOnClose
			>
				<Form
					form={adminForm}
					layout="vertical"
					onFinish={(values) =>
						createAdminMutation.mutate({
							...values,
							workspaceRole: "WORKSPACE_ADMIN",
						})
					}
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
					<Form.Item label="工作区角色">
						<Input value="WORKSPACE_ADMIN" disabled />
					</Form.Item>
					{createAdminMutation.isError && (
						<Form.Item>
							<ApiErrorAlert error={createAdminMutation.error} />
						</Form.Item>
					)}
				</Form>
			</Modal>

			<Modal
				title="新增成员"
				open={createMemberModalOpen}
				onCancel={() => {
					setCreateMemberModalOpen(false);
					resetMemberProvisionWizard();
				}}
				destroyOnClose
				width={900}
				footer={
					memberProvisionStep === 0 ? (
						<Space>
							<Button
								onClick={() => {
									setCreateMemberModalOpen(false);
									resetMemberProvisionWizard();
								}}
							>
								取消
							</Button>
							<Button
								type="primary"
								onClick={async () => {
									const values = await memberForm.validateFields();
									setMemberDraft({
										username: values.username.trim(),
										displayName: values.displayName.trim(),
										password: values.password,
									});
									setMemberProvisionStep(1);
								}}
							>
								下一步
							</Button>
						</Space>
					) : (
						<Space>
							<Button onClick={() => setMemberProvisionStep(0)}>上一步</Button>
							<Button
								type="primary"
								loading={createMemberMutation.isPending}
								disabled={
									selectedKnowledgeBaseIds.length === 0 || memberDraft === null
								}
								onClick={async () => {
									if (!memberDraft) {
										setMemberProvisionStep(0);
										return;
									}
									await createMemberMutation.mutateAsync({
										username: memberDraft.username,
										displayName: memberDraft.displayName,
										password: memberDraft.password,
										initialKnowledgeBaseGrants: selectedKnowledgeBaseIds.map(
											(kbId) => ({
												kbId,
												role: knowledgeBaseRoles[kbId] ?? "KB_READER",
											}),
										),
									});
								}}
							>
								创建成员
							</Button>
						</Space>
					)
				}
			>
				<Steps
					current={memberProvisionStep}
					items={[
						{ title: "基础信息" },
						{ title: "初始知识库授权" },
					]}
					style={{ marginBottom: 24 }}
				/>
				{memberProvisionStep === 0 ? (
					<Form form={memberForm} layout="vertical">
						<Form.Item
							name="username"
							label="用户名"
							rules={[
								{ required: true, message: "请输入用户名" },
								{
									validator: async (_, value: string | undefined) => {
										if (!value || value.trim().length === 0) {
											throw new Error("请输入用户名");
										}
									},
								},
							]}
						>
							<Input />
						</Form.Item>
						<Form.Item
							name="displayName"
							label="显示名"
							rules={[
								{ required: true, message: "请输入显示名" },
								{
									validator: async (_, value: string | undefined) => {
										if (!value || value.trim().length === 0) {
											throw new Error("请输入显示名");
										}
									},
								},
							]}
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
					</Form>
				) : (
					<Space direction="vertical" size={16} style={{ width: "100%" }}>
						<Typography.Paragraph type="secondary" style={{ margin: 0 }}>
							至少选择一个知识库，并为成员指定初始角色。
						</Typography.Paragraph>
						{selectedKnowledgeBaseIds.length === 0 && (
							<Typography.Text type="danger">
								请至少选择一个知识库授权后再创建成员。
							</Typography.Text>
						)}
						{createMemberMutation.isError && (
							<ApiErrorAlert error={createMemberMutation.error} />
						)}
						<Table<KnowledgeBase>
							rowKey="id"
							pagination={false}
							loading={knowledgeQuery.isLoading}
							dataSource={activeKnowledgeBases}
							columns={[
								{
									title: "授权",
									dataIndex: "id",
									width: 80,
									render: (kbId: string) => (
										<Checkbox
											checked={selectedKnowledgeBaseIds.includes(kbId)}
											onChange={(event) => {
												if (event.target.checked) {
													setSelectedKnowledgeBaseIds((prev) =>
														prev.includes(kbId) ? prev : [...prev, kbId],
													);
													setKnowledgeBaseRoles((prev) => ({
														...prev,
														[kbId]: prev[kbId] ?? "KB_READER",
													}));
													return;
												}
												setSelectedKnowledgeBaseIds((prev) =>
													prev.filter((item) => item !== kbId),
												);
											}}
										/>
									),
								},
								{ title: "知识库名称", dataIndex: "name", width: 220 },
								{ title: "知识库 ID", dataIndex: "id", width: 220 },
								{
									title: "初始角色",
									dataIndex: "id",
									render: (kbId: string) => (
										<Select
											style={{ width: "100%" }}
											disabled={!selectedKnowledgeBaseIds.includes(kbId)}
											value={knowledgeBaseRoles[kbId] ?? "KB_READER"}
											options={knowledgeRoleOptions.map((item) => ({
												value: item.value,
												label: item.label,
											}))}
											onChange={(
												value: (typeof knowledgeRoleOptions)[number]["value"],
											) =>
												setKnowledgeBaseRoles((prev) => ({
													...prev,
													[kbId]: value,
												}))
											}
										/>
									),
								},
							]}
						/>
					</Space>
				)}
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
