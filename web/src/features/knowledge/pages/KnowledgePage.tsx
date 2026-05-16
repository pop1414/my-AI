import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	Button,
	Card,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Table,
	Tag,
	Typography,
	message,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { z } from "zod";
import {
	createKnowledgeBase,
	listKnowledgeBases,
	type KnowledgeBase,
	updateKnowledgeBase,
} from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { useAuth } from "../../../shared/auth/AuthContext";
import { ConsoleLinkButton } from "../../../shared/ui/ConsoleLinkButton";
import {
	ConsoleBadgeRow,
	ConsoleMetricCards,
	ConsolePageFrame,
	ConsoleStatePanel,
	type ConsoleMetricItem,
	type ConsoleStateTone,
} from "../../../shared/ui/console/ConsolePageFrame";

const { Text } = Typography;

const knowledgeBaseCreateSchema = z.object({
	name: z
		.string()
		.trim()
		.min(1, "知识库名称不能为空")
		.max(100, "名称最长 100 字符"),
	description: z.string().trim().max(500, "描述最长 500 字符").optional(),
	status: z.enum(["ACTIVE", "INACTIVE"]).default("ACTIVE"),
});

const knowledgeBaseUpdateSchema = knowledgeBaseCreateSchema;

function statusColor(status: KnowledgeBase["status"]): string {
	return status === "ACTIVE" ? "success" : "default";
}

const columns = (
	onEdit: (record: KnowledgeBase) => void,
	canManageKnowledgeBases: boolean,
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
				{canManageKnowledgeBases && (
					<Button size="small" onClick={() => onEdit(record)}>
						编辑
					</Button>
				)}
				<ConsoleLinkButton
					to={`/qa?kbId=${encodeURIComponent(record.id)}`}
					variant="primary"
					size="small"
					disabled={record.status !== "ACTIVE"}
					onClick={() => {
						localStorage.setItem("myai:lastKbId", record.id);
					}}
				>
					去问答
				</ConsoleLinkButton>
				{canManageKnowledgeBases && (
					<ConsoleLinkButton
						variant="default"
						size="small"
						to={`/admin/knowledge-bases/${encodeURIComponent(record.id)}/grants`}
					>
						授权管理
					</ConsoleLinkButton>
				)}
			</Space>
		),
	},
];

function resolveCollectionTone(params: {
	isLoading: boolean;
	isError: boolean;
	isEmpty: boolean;
}): ConsoleStateTone {
	if (params.isLoading) {
		return "loading";
	}
	if (params.isError) {
		return "error";
	}
	if (params.isEmpty) {
		return "empty";
	}
	return "ready";
}

export function KnowledgePage() {
	const queryClient = useQueryClient();
	const { user } = useAuth();
	const canManageKnowledgeBases = Boolean(user?.capabilities.canAccessAdmin);
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
			message.success("知识库已创建");
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
			message.success("知识库已更新");
		},
	});

	const onCreate = async () => {
		const values = knowledgeBaseCreateSchema.parse(
			createForm.getFieldsValue(),
		);
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

	const knowledgeBases = knowledgeQuery.data ?? [];
	const activeKnowledgeBases = knowledgeBases.filter(
		(item) => item.status === "ACTIVE",
	);
	const inactiveKnowledgeBases = knowledgeBases.filter(
		(item) => item.status === "INACTIVE",
	);
	const totalIndexedDocuments = knowledgeBases.reduce(
		(total, item) => total + item.indexedDocumentCount,
		0,
	);
	const firstActiveKnowledgeBaseId = activeKnowledgeBases[0]?.id;
	const listStateTone = resolveCollectionTone({
		isLoading: knowledgeQuery.isLoading,
		isError: knowledgeQuery.isError,
		isEmpty: !knowledgeQuery.isLoading && !knowledgeQuery.isError && knowledgeBases.length === 0,
	});

	const metricItems: ConsoleMetricItem[] = useMemo(
		() => [
			{
				key: "total",
				label: "知识库总数",
				value: knowledgeQuery.isLoading ? "同步中" : knowledgeBases.length,
				hint: "共享目录中的全部知识库实体",
			},
			{
				key: "active",
				label: "可问答知识库",
				value: knowledgeQuery.isLoading ? "--" : activeKnowledgeBases.length,
				hint: "可直接承接问答入口的 ACTIVE 知识库",
				accent: "teal",
			},
			{
				key: "indexed",
				label: "索引文档总量",
				value: knowledgeQuery.isLoading ? "--" : totalIndexedDocuments,
				hint: "按知识库汇总的已索引文档数",
				accent: "slate",
			},
			{
				key: "inactive",
				label: "待治理项",
				value: knowledgeQuery.isLoading ? "--" : inactiveKnowledgeBases.length,
				hint: "当前处于停用态的知识库数量",
				accent: "amber",
			},
		],
		[
			activeKnowledgeBases.length,
			inactiveKnowledgeBases.length,
			knowledgeBases.length,
			knowledgeQuery.isLoading,
			totalIndexedDocuments,
		],
	);

	let listStateTitle = "知识库目录已就绪";
	let listStateDescription =
		"可以在主工作区浏览知识库目录、进入问答链路，并从状态区查看治理提示。";
	if (listStateTone === "loading") {
		listStateTitle = "正在同步知识库目录";
		listStateDescription =
			"页面骨架已固定，目录和摘要数据正在从共享接口拉取。";
	}
	if (listStateTone === "empty") {
		listStateTitle = "尚未配置知识库";
		listStateDescription =
			"当前工作区没有可承接问答的知识库，请先创建知识库或回填历史数据。";
	}
	if (listStateTone === "error") {
		listStateTitle = "知识库目录暂不可用";
		listStateDescription =
			"接口返回异常，目录未能同步成功。请查看下方错误详情并稍后重试。";
	}

	const collectionStatusExtra =
		listStateTone === "error" ? (
			<ApiErrorAlert error={knowledgeQuery.error} />
		) : listStateTone === "empty" && canManageKnowledgeBases ? (
			<Button type="primary" onClick={() => createForm.scrollToField("name")}>
				开始创建知识库
			</Button>
		) : null;

	const operationStatusCard = createMutation.isPending ? (
		<ConsoleStatePanel
			tone="loading"
			title="正在创建知识库"
			description="提交成功后会自动刷新目录、摘要和治理状态。"
			testId="knowledge-operation-status"
		/>
	) : updateMutation.isPending ? (
		<ConsoleStatePanel
			tone="loading"
			title="正在更新知识库"
			description="修改完成后会刷新清单，确保问答入口与授权链路看到最新状态。"
			testId="knowledge-operation-status"
		/>
	) : createMutation.isError ? (
		<ConsoleStatePanel
			tone="error"
			title="创建知识库失败"
			description="提交未完成，当前工作区仍保持原有目录状态。"
			extra={<ApiErrorAlert error={createMutation.error} />}
			testId="knowledge-operation-status"
		/>
	) : updateMutation.isError ? (
		<ConsoleStatePanel
			tone="error"
			title="更新知识库失败"
			description="请检查输入内容或服务端状态，再重新提交。"
			extra={<ApiErrorAlert error={updateMutation.error} />}
			testId="knowledge-operation-status"
		/>
	) : (
		<ConsoleStatePanel
			tone={canManageKnowledgeBases ? "ready" : "warning"}
			title={canManageKnowledgeBases ? "治理入口已开放" : "当前为只读视图"}
			description={
				canManageKnowledgeBases
					? "当前账号可维护知识库主数据，并继续进入授权管理与问答验证。"
					: "当前账号可查看目录和问答入口，但不能修改知识库配置。"
			}
			testId="knowledge-operation-status"
		/>
	);

	return (
		<>
			<ConsolePageFrame
				eyebrow="Knowledge Workspace"
				title="知识库总览"
				description="把知识库主数据、问答入口和治理提醒收敛到统一页面骨架中，后续同类页面可直接复用这套摘要区、主工作区和状态区模式。"
				badges={
					<ConsoleBadgeRow
						items={[
							{ label: "共享骨架已落地", color: "cyan" },
							{
								label: canManageKnowledgeBases ? "当前角色：可治理" : "当前角色：只读",
								color: canManageKnowledgeBases ? "geekblue" : "default",
							},
							{ label: "GET /api/v1/knowledge-bases" },
						]}
					/>
				}
				actions={
					<ConsoleLinkButton
						variant="primary"
						to={
							firstActiveKnowledgeBaseId
								? `/qa?kbId=${encodeURIComponent(firstActiveKnowledgeBaseId)}`
								: "/qa"
						}
						disabled={!firstActiveKnowledgeBaseId}
						onClick={() => {
							if (!firstActiveKnowledgeBaseId) {
								return;
							}
							localStorage.setItem("myai:lastKbId", firstActiveKnowledgeBaseId);
						}}
					>
						进入问答验证
					</ConsoleLinkButton>
				}
				summary={<ConsoleMetricCards items={metricItems} />}
				status={
					<>
						<ConsoleStatePanel
							tone={listStateTone}
							title={listStateTitle}
							description={listStateDescription}
							extra={collectionStatusExtra}
							testId="knowledge-query-status"
						/>
						{operationStatusCard}
						<ConsoleStatePanel
							tone="ready"
							title="共享状态区"
							description="这里保留页面级状态和治理提示，避免把列表异常、操作反馈和角色差异散落在工作区内部。"
							testId="knowledge-shared-status"
						/>
					</>
				}
			>
				{canManageKnowledgeBases && (
					<Card title="新建知识库" data-testid="knowledge-create-card">
						<Form
							form={createForm}
							layout="vertical"
							initialValues={{
								name: "",
								description: "",
								status: "ACTIVE",
							}}
							onFinish={onCreate}
						>
							<Form.Item label="名称" name="name">
								<Input
									placeholder="例如：产品文档库"
									maxLength={100}
									showCount
								/>
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
				)}

				<Card
					title="知识库清单"
					extra={
						<Text type="secondary">
							{knowledgeQuery.isLoading
								? "目录同步中"
								: `已加载 ${knowledgeBases.length} 个知识库`}
						</Text>
					}
				>
					{knowledgeQuery.isLoading ? (
						<ConsoleStatePanel
							tone="loading"
							title="正在同步知识库目录"
							description="请稍候，主工作区会在数据返回后自动切换到清单视图。"
							testId="knowledge-list-state"
						/>
					) : knowledgeQuery.isError ? (
						<ConsoleStatePanel
							tone="error"
							title="知识库目录暂不可用"
							description="列表加载失败，当前无法展示知识库清单。"
							extra={<ApiErrorAlert error={knowledgeQuery.error} />}
							testId="knowledge-list-state"
						/>
					) : knowledgeBases.length === 0 ? (
						<ConsoleStatePanel
							tone="empty"
							title="尚未配置知识库"
							description="当前没有任何知识库记录。可以先创建知识库，或等待历史数据回填完成。"
							testId="knowledge-list-state"
						/>
					) : (
						<Table
							rowKey="id"
							columns={columns(onEdit, canManageKnowledgeBases)}
							dataSource={knowledgeBases}
							loading={knowledgeQuery.isFetching}
							pagination={false}
						/>
					)}
				</Card>
			</ConsolePageFrame>

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
		</>
	);
}
