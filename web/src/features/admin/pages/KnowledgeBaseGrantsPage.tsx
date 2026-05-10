import { useState } from "react";
import { useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	Button,
	Form,
	Modal,
	Popconfirm,
	Select,
	Table,
	Tag,
	Typography,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import {
	deleteKnowledgeBaseGrant,
	listKnowledgeBaseGrants,
	listMembers,
	upsertKnowledgeBaseGrant,
	type KnowledgeBaseGrant,
} from "../../../shared/api/adminApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import type { ApiError } from "../../../shared/api/request";

const { Title } = Typography;

const roleColorMap: Record<string, string> = {
	KB_MANAGER: "red",
	KB_CONTRIBUTOR: "blue",
	KB_READER: "green",
	KB_ASKER: "default",
};

const roleOptions: { value: KnowledgeBaseGrant["role"]; label: string }[] = [
	{ value: "KB_MANAGER", label: "KB_MANAGER" },
	{ value: "KB_CONTRIBUTOR", label: "KB_CONTRIBUTOR" },
	{ value: "KB_READER", label: "KB_READER" },
	{ value: "KB_ASKER", label: "KB_ASKER" },
];

export function KnowledgeBaseGrantsPage() {
	const { kbId } = useParams<{ kbId: string }>();
	const queryClient = useQueryClient();
	const [modalOpen, setModalOpen] = useState(false);
	const [grantForm] = Form.useForm<{
		userId: string;
		role: KnowledgeBaseGrant["role"];
	}>();

	// 查询知识库名称
	const kbQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
		select: (data) => data.find((kb) => kb.id === kbId),
	});

	// 查询授权列表
	const grantsQuery = useQuery({
		queryKey: ["admin", "knowledge-base-grants", kbId],
		queryFn: () => listKnowledgeBaseGrants(kbId!),
		enabled: !!kbId,
	});

	// 成员列表（作为成员选择器数据源）
	const membersQuery = useQuery({
		queryKey: ["admin", "members"],
		queryFn: listMembers,
	});

	const upsertMutation = useMutation({
		mutationFn: (params: {
			userId: string;
			role: KnowledgeBaseGrant["role"];
		}) => upsertKnowledgeBaseGrant(kbId!, params.userId, params.role),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: ["admin", "knowledge-base-grants", kbId],
			});
			setModalOpen(false);
			grantForm.resetFields();
		},
	});

	const deleteMutation = useMutation({
		mutationFn: (userId: string) => deleteKnowledgeBaseGrant(kbId!, userId),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: ["admin", "knowledge-base-grants", kbId],
			});
		},
	});

	const columns: ColumnsType<KnowledgeBaseGrant> = [
		{ title: "用户名", dataIndex: "username", width: 160 },
		{ title: "显示名", dataIndex: "displayName", width: 160 },
		{
			title: "知识库角色",
			dataIndex: "role",
			width: 180,
			render: (value: string) => (
				<Tag color={roleColorMap[value] ?? "default"}>{value}</Tag>
			),
		},
		{
			title: "授权状态",
			dataIndex: "status",
			width: 120,
			render: (value: string) => <Tag color="success">{value}</Tag>,
		},
		{
			title: "操作",
			key: "action",
			width: 120,
			render: (_, record) => (
				<Popconfirm
					title="确认回收授权？"
					description={`将回收 ${record.displayName || record.username} 的知识库授权`}
					onConfirm={() => deleteMutation.mutate(record.userId)}
					okText="确认回收"
					cancelText="取消"
				>
					<a>回收授权</a>
				</Popconfirm>
			),
		},
	];

	const grantsError = grantsQuery.error as ApiError | null;
	const kbName = kbQuery.data?.name ?? kbId;

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
					知识库授权管理 · {kbName}
				</Title>
				<Button
					type="primary"
					icon={<PlusOutlined />}
					onClick={() => {
						grantForm.resetFields();
						setModalOpen(true);
					}}
				>
					新增授权
				</Button>
			</div>

			{grantsError && (
				<div style={{ marginBottom: 16 }}>
					<ApiErrorAlert error={grantsError} />
				</div>
			)}

			<Table<KnowledgeBaseGrant>
				columns={columns}
				dataSource={grantsQuery.data ?? []}
				rowKey="userId"
				loading={grantsQuery.isLoading}
				pagination={false}
				locale={{ emptyText: "暂无授权记录" }}
			/>

			<Modal
				title="新增 / 编辑知识库授权"
				open={modalOpen}
				onOk={() => grantForm.submit()}
				onCancel={() => {
					setModalOpen(false);
					grantForm.resetFields();
				}}
				confirmLoading={upsertMutation.isPending}
				destroyOnClose
			>
				<Form
					form={grantForm}
					layout="vertical"
					onFinish={(values) => {
						upsertMutation.mutate(values);
					}}
				>
					<Form.Item
						name="userId"
						label="成员"
						rules={[{ required: true, message: "请选择成员" }]}
					>
						<Select
							showSearch
							placeholder="搜索并选择成员"
							filterOption={(input, option) =>
								(option?.label as string)
									?.toLowerCase()
									.includes(input.toLowerCase()) ?? false
							}
							options={(membersQuery.data ?? []).map((m) => ({
								value: m.userId,
								label: `${m.displayName || m.username} (${m.username})`,
							}))}
							loading={membersQuery.isLoading}
						/>
					</Form.Item>
					<Form.Item
						name="role"
						label="知识库角色"
						rules={[{ required: true, message: "请选择角色" }]}
					>
						<Select options={roleOptions} />
					</Form.Item>
					{upsertMutation.error && (
						<Form.Item>
							<ApiErrorAlert error={upsertMutation.error} />
						</Form.Item>
					)}
				</Form>
			</Modal>
		</div>
	);
}
