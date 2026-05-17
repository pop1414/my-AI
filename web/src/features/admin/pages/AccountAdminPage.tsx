import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	Button,
	Checkbox,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Steps,
	Table,
	Tag,
	Typography,
	message,
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
import { formatTime } from "../../ingest/utils/formatters";
import { AccountStatusTags } from "../components/AccountStatusTags";
import { ManagedAccountTableActions } from "../components/ManagedAccountTableActions";
import { AdminAccountForm, PasswordResetForm } from "../components/AccountForms";

const { Title } = Typography;

const knowledgeRoleOptions = [
	{ value: "KB_MANAGER", label: "管理者" },
	{ value: "KB_CONTRIBUTOR", label: "贡献者" },
	{ value: "KB_READER", label: "读者" },
	{ value: "KB_ASKER", label: "问答者" },
] as const;

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
	const [adminForm] = Form.useForm();
	const [memberForm] = Form.useForm();
	const [passwordForm] = Form.useForm();

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
			message.success("管理员账号已创建");
		},
	});

	const createMemberMutation = useMutation({
		mutationFn: createManagedMember,
		onSuccess: (created) => {
			queryClient.invalidateQueries({ queryKey: ["admin", "accounts"] });
			queryClient.invalidateQueries({ queryKey: ["admin", "members"] });
			setCreateMemberModalOpen(false);
			resetMemberProvisionWizard();
			message.success("成员开户成功，正在进入授权配置页");
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
			message.success("账号状态已更新");
		},
	});

	const passwordMutation = useMutation({
		mutationFn: (params: { userId: string; password: string }) =>
			resetManagedAccountPassword(params.userId, params.password),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["admin", "accounts"] });
			setPasswordModalAccount(null);
			passwordForm.resetFields();
			message.success("密码已重置");
		},
	});

	const removeMutation = useMutation({
		mutationFn: (userId: string) => removeManagedAccountMembership(userId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ["admin", "accounts"] });
			message.success("成员关系已移除");
		},
	});

	const currentRole = user?.workspaceRole;
	const canManageAccount = (record: ManagedAccount) => {
		if (record.workspaceRole === "WORKSPACE_OWNER") return false;
		if (currentRole === "WORKSPACE_OWNER") return true;
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
		{ 
			title: "用户/显示名", 
			dataIndex: "username", 
			width: 220,
			render: (_, record) => (
				<Space direction="vertical" size={0}>
					<span style={{ fontWeight: 500 }}>{record.displayName}</span>
					<Typography.Text type="secondary" style={{ fontSize: 12, fontFamily: 'var(--console-font-mono)' }}>
						@{record.username}
					</Typography.Text>
				</Space>
			)
		},
		{
			title: "角色与状态",
			width: 240,
			render: (_, record) => (
				<Space size={4} wrap>
					<Tag bordered={false} color="default" style={{ borderRadius: 4, fontWeight: 500 }}>
						{record.workspaceRole}
					</Tag>
					<AccountStatusTags
						userStatus={record.userStatus}
						membershipStatus={record.membershipStatus}
					/>
				</Space>
			),
		},
		{
			title: "安全审计",
			width: 260,
			render: (_, record) => (
				<Space direction="vertical" size={0}>
					<Typography.Text type="secondary" style={{ fontSize: 12 }}>
						登录失败：{record.failedLoginCount} 次
					</Typography.Text>
					{record.lockedUntil && (
						<Typography.Text type="danger" style={{ fontSize: 12 }}>
							锁定至：{formatTime(record.lockedUntil)}
						</Typography.Text>
					)}
				</Space>
			),
		},
		{
			title: "操作",
			key: "action",
			width: 280,
			fixed: 'right',
			render: (_, record) => (
				<ManagedAccountTableActions
					record={record}
					canManage={canManageAccount(record)}
					statusPending={statusMutation.isPending && statusMutation.variables?.userId === record.userId}
					onStatusUpdate={(r) => statusMutation.mutate({
						userId: r.userId,
						userStatus: r.userStatus === "ACTIVE" ? "DISABLED" : "ACTIVE",
					})}
					onPasswordReset={setPasswordModalAccount}
					onMemberRemove={removeMutation.mutate}
				/>
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
					{currentRole === "WORKSPACE_OWNER" && (
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
			{(statusMutation.isError || removeMutation.isError) && (
				<div style={{ marginBottom: 16 }}>
					<ApiErrorAlert error={statusMutation.error || removeMutation.error} />
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
				<AdminAccountForm
					form={adminForm}
					onFinish={(values) =>
						createAdminMutation.mutate({
							...values,
							workspaceRole: "WORKSPACE_ADMIN",
						})
					}
				/>
				{createAdminMutation.isError && (
					<div style={{ marginTop: 12 }}>
						<ApiErrorAlert error={createAdminMutation.error} />
					</div>
				)}
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
							<Input placeholder="用户名" />
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
							<Input placeholder="显示名" />
						</Form.Item>
						<Form.Item
							name="password"
							label="初始密码"
							rules={[{ required: true, message: "请输入初始密码" }]}
						>
							<Input.Password placeholder="初始密码" />
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
				<PasswordResetForm
					form={passwordForm}
					targetName={passwordModalAccount?.displayName ?? passwordModalAccount?.username ?? ""}
					onFinish={(values) => {
						if (passwordModalAccount) {
							passwordMutation.mutate({
								userId: passwordModalAccount.userId,
								password: values.password,
							});
						}
					}}
				/>
				{passwordMutation.isError && (
					<div style={{ marginTop: 12 }}>
						<ApiErrorAlert error={passwordMutation.error} />
					</div>
				)}
			</Modal>
		</div>
	);
}
