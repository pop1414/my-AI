import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	Button,
	Card,
	Empty,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import {
	createKnowledgeBase,
	listKnowledgeBases,
	type KnowledgeBase,
	updateKnowledgeBase,
} from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

const knowledgeBaseCreateSchema = z.object({
	name: z.string().trim().min(1, "知识库名称不能为空").max(100, "名称最长 100 字符"),
	description: z.string().trim().max(500, "描述最长 500 字符").optional(),
	status: z.enum(["ACTIVE", "INACTIVE"]).default("ACTIVE"),
});

const knowledgeBaseUpdateSchema = knowledgeBaseCreateSchema;

function statusColor(status: KnowledgeBase["status"]): string {
	return status === "ACTIVE" ? "success" : "default";
}

const columns = (
	navigate: ReturnType<typeof useNavigate>,
	onEdit: (record: KnowledgeBase) => void,
): ColumnsType<KnowledgeBase> => [
	{ title: "知识库 ID", dataIndex: "id", width: 320 },
	{ title: "名称", dataIndex: "name", width: 180 },
	{
		title: "状态",
		dataIndex: "status",
		width: 120,
		render: (value: KnowledgeBase["status"]) => (
			<Tag color={statusColor(value)}>{value}</Tag>
		),
	},
	{ title: "已索引文档数", dataIndex: "indexedDocumentCount", width: 140 },
	{
		title: "描述",
		dataIndex: "description",
		render: (value: string) => value || "-",
	},
	{
		title: "操作",
		key: "action",
		width: 220,
		render: (_, record) => (
			<Space>
				<Button size="small" onClick={() => onEdit(record)}>
					编辑
				</Button>
				<Button
					type="primary"
					size="small"
					disabled={record.status !== "ACTIVE"}
					onClick={() => {
						localStorage.setItem("myai:lastKbId", record.id);
						navigate(`/qa?kbId=${encodeURIComponent(record.id)}`);
					}}
				>
					去问答
				</Button>
			</Space>
		),
	},
];

export function KnowledgePage() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const [createForm] = Form.useForm<{
		name: string;
		description?: string;
		status: "ACTIVE" | "INACTIVE";
	}>();
	const [editForm] = Form.useForm<{
		name: string;
		description?: string;
		status: "ACTIVE" | "INACTIVE";
	}>();
	const [editingKnowledgeBase, setEditingKnowledgeBase] =
		useState<KnowledgeBase | null>(null);

	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});
	const createMutation = useMutation({
		mutationFn: createKnowledgeBase,
		onSuccess: () => {
			createForm.resetFields();
			queryClient.invalidateQueries({ queryKey: ["knowledge-bases"] });
		},
	});
	const updateMutation = useMutation({
		mutationFn: ({
			kbId,
			values,
		}: {
			kbId: string;
			values: {
				name?: string;
				description?: string;
				status?: "ACTIVE" | "INACTIVE";
			};
		}) => updateKnowledgeBase(kbId, values),
		onSuccess: () => {
			setEditingKnowledgeBase(null);
			queryClient.invalidateQueries({ queryKey: ["knowledge-bases"] });
		},
	});

	const onCreate = async () => {
		const values = knowledgeBaseCreateSchema.parse(createForm.getFieldsValue());
		await createMutation.mutateAsync(values);
	};

	const onEdit = (record: KnowledgeBase) => {
		setEditingKnowledgeBase(record);
		editForm.setFieldsValue({
			name: record.name,
			description: record.description,
			status: record.status,
		});
	};

	const onUpdate = async () => {
		if (!editingKnowledgeBase) {
			return;
		}
		const values = knowledgeBaseUpdateSchema.parse(editForm.getFieldsValue());
		await updateMutation.mutateAsync({
			kbId: editingKnowledgeBase.id,
			values,
		});
	};

	return (
		<Space direction="vertical" size={16} style={{ width: "100%" }}>
			<Card
				title="知识库列表"
				extra={
					<Typography.Text type="secondary">
						GET /api/v1/knowledge-bases
					</Typography.Text>
				}
			>
				<Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
					管理知识库主数据，并查看各知识库的已索引文档数量。
				</Typography.Paragraph>
			</Card>

			{knowledgeQuery.isError && <ApiErrorAlert error={knowledgeQuery.error} />}
			{createMutation.isError && <ApiErrorAlert error={createMutation.error} />}
			{updateMutation.isError && <ApiErrorAlert error={updateMutation.error} />}

			<Card title="新建知识库">
				<Form
					form={createForm}
					layout="vertical"
					initialValues={{ name: "", description: "", status: "ACTIVE" }}
					onFinish={onCreate}
				>
					<Form.Item label="名称" name="name">
						<Input placeholder="例如：产品文档库" maxLength={100} showCount />
					</Form.Item>
					<Form.Item label="描述" name="description">
						<Input.TextArea rows={3} maxLength={500} showCount />
					</Form.Item>
					<Form.Item label="状态" name="status">
						<Select
							options={[
								{ label: "ACTIVE", value: "ACTIVE" },
								{ label: "INACTIVE", value: "INACTIVE" },
							]}
						/>
					</Form.Item>
					<Button
						type="primary"
						htmlType="submit"
						loading={createMutation.isPending}
					>
						创建知识库
					</Button>
				</Form>
			</Card>

			<Card>
				{knowledgeQuery.isLoading ? (
					<Table
						loading
						columns={columns(navigate, onEdit)}
						dataSource={[]}
						rowKey="id"
					/>
				) : knowledgeQuery.data && knowledgeQuery.data.length === 0 ? (
					<Empty description="暂无知识库，请先创建知识库或上传历史数据完成回填。" />
				) : (
					<Table
						rowKey="id"
						columns={columns(navigate, onEdit)}
						dataSource={knowledgeQuery.data}
						loading={knowledgeQuery.isFetching}
						pagination={false}
					/>
				)}
			</Card>

			<Modal
				title="编辑知识库"
				open={editingKnowledgeBase !== null}
				onCancel={() => setEditingKnowledgeBase(null)}
				onOk={() => void editForm.submit()}
				confirmLoading={updateMutation.isPending}
				destroyOnHidden
			>
				<Form form={editForm} layout="vertical" onFinish={onUpdate}>
					<Form.Item label="名称" name="name">
						<Input maxLength={100} showCount />
					</Form.Item>
					<Form.Item label="描述" name="description">
						<Input.TextArea rows={3} maxLength={500} showCount />
					</Form.Item>
					<Form.Item label="状态" name="status">
						<Select
							options={[
								{ label: "ACTIVE", value: "ACTIVE" },
								{ label: "INACTIVE", value: "INACTIVE" },
							]}
						/>
					</Form.Item>
				</Form>
			</Modal>
		</Space>
	);
}
