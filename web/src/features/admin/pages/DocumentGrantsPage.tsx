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
	deleteDocumentGrant,
	listDocumentGrants,
	listMembers,
	upsertDocumentGrant,
	type DocumentGrant,
} from "../../../shared/api/adminApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import type { ApiError } from "../../../shared/api/request";

const { Title } = Typography;

const permissionColorMap: Record<string, string> = {
	DOC_ALLOW_READ: "green",
	DOC_ALLOW_MANAGE: "blue",
	DOC_DENY: "red",
};

const permissionOptions: {
	value: DocumentGrant["permission"];
	label: string;
}[] = [
	{ value: "DOC_ALLOW_READ", label: "DOC_ALLOW_READ" },
	{ value: "DOC_ALLOW_MANAGE", label: "DOC_ALLOW_MANAGE" },
	{ value: "DOC_DENY", label: "DOC_DENY" },
];

export function DocumentGrantsPage() {
	const { documentId } = useParams<{ documentId: string }>();
	const queryClient = useQueryClient();
	const [modalOpen, setModalOpen] = useState(false);
	const [grantForm] = Form.useForm<{
		userId: string;
		permission: DocumentGrant["permission"];
	}>();

	// 查询授权列表
	const grantsQuery = useQuery({
		queryKey: ["admin", "document-grants", documentId],
		queryFn: () => listDocumentGrants(documentId!),
		enabled: !!documentId,
	});

	// 成员列表（作为成员选择器数据源）
	const membersQuery = useQuery({
		queryKey: ["admin", "members"],
		queryFn: listMembers,
	});

	const upsertMutation = useMutation({
		mutationFn: (params: {
			userId: string;
			permission: DocumentGrant["permission"];
		}) =>
			upsertDocumentGrant(documentId!, params.userId, params.permission),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: ["admin", "document-grants", documentId],
			});
			setModalOpen(false);
			grantForm.resetFields();
		},
	});

	const deleteMutation = useMutation({
		mutationFn: (userId: string) =>
			deleteDocumentGrant(documentId!, userId),
		onSuccess: () => {
			queryClient.invalidateQueries({
				queryKey: ["admin", "document-grants", documentId],
			});
		},
	});

	const columns: ColumnsType<DocumentGrant> = [
		{ title: "用户名", dataIndex: "username", width: 160 },
		{ title: "显示名", dataIndex: "displayName", width: 160 },
		{
			title: "文档权限",
			dataIndex: "permission",
			width: 180,
			render: (value: string) => (
				<Tag color={permissionColorMap[value] ?? "default"}>
					{value}
				</Tag>
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
					description={`将回收 ${record.displayName || record.username} 的文档授权`}
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
					文档授权管理 · {documentId}
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

			<Table<DocumentGrant>
				columns={columns}
				dataSource={grantsQuery.data ?? []}
				rowKey="userId"
				loading={grantsQuery.isLoading}
				pagination={false}
				locale={{ emptyText: "暂无授权记录" }}
			/>

			<Modal
				title="新增 / 编辑文档授权"
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
						name="permission"
						label="文档权限"
						rules={[{ required: true, message: "请选择权限" }]}
					>
						<Select options={permissionOptions} />
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
