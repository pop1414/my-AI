import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
	Button,
	Drawer,
	Form,
	Typography,
	message,
	Space,
	Empty,
	Row,
	Col,
	Card,
	Tooltip,
	Input,
	Select,
} from "antd";
import {
	PlusOutlined,
	DatabaseOutlined,
	CommentOutlined,
	FileTextOutlined,
	SettingOutlined,
	TeamOutlined,
	CopyOutlined,
	SearchOutlined,
	DeleteOutlined,
	ReloadOutlined,
} from "@ant-design/icons";
import { z } from "zod";
import {
	createKnowledgeBase,
	listKnowledgeBasesWithOptions,
	type KnowledgeBase,
	updateKnowledgeBase,
	deleteKnowledgeBase,
} from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { useAuth } from "../../../shared/auth/AuthContext";
import {
	ConsolePageFrame,
} from "../../../shared/ui/console/ConsolePageFrame";

import { KnowledgeBaseForm } from "../components/KnowledgeBaseForm";
import { KnowledgeBaseStatusTag } from "../components/KnowledgeBaseStatusTag";
import { DeleteKnowledgeBaseConfirmModal } from "../components/DeleteKnowledgeBaseConfirmModal";
import "./KnowledgePage.css";

const { Text, Title, Paragraph } = Typography;

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

export function KnowledgePage() {
	const navigate = useNavigate();
	const queryClient = useQueryClient();
	const { user } = useAuth();
	const [form] = Form.useForm();
	const [filterForm] = Form.useForm();
	const [drawerOpen, setDrawerOpen] = useState(false);
	const [editingKb, setEditingKb] = useState<KnowledgeBase | null>(null);
	const [deleteTarget, setDeleteTarget] = useState<KnowledgeBase | null>(null);
	const [filters, setFilters] = useState({
		keyword: "",
		status: "ALL",
	});

	const canAccessAdmin = Boolean(user?.capabilities.canAccessAdmin);

	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases", { includeDeleted: canAccessAdmin }],
		queryFn: () => listKnowledgeBasesWithOptions({ includeDeleted: canAccessAdmin }),
	});

	const createMutation = useMutation({
		mutationFn: createKnowledgeBase,
		onSuccess: () => {
			setDrawerOpen(false);
			form.resetFields();
			queryClient.invalidateQueries({ queryKey: ["knowledge-bases"] });
			message.success("知识库已成功创建");
		},
	});

	const updateMutation = useMutation({
		mutationFn: ({
			kbId,
			values,
		}: {
			kbId: string;
			values: Record<string, unknown>;
		}) => updateKnowledgeBase(kbId, values),
		onSuccess: () => {
			setDrawerOpen(false);
			setEditingKb(null);
			form.resetFields();
			queryClient.invalidateQueries({ queryKey: ["knowledge-bases"] });
			message.success("知识库配置已更新");
		},
	});

	const deleteMutation = useMutation({
		mutationFn: deleteKnowledgeBase,
		onSuccess: () => {
			setDeleteTarget(null);
			queryClient.invalidateQueries({ queryKey: ["knowledge-bases"] });
			message.success("知识库已成功删除");
		},
	});

	const handleOpenCreate = () => {
		setEditingKb(null);
		form.resetFields();
		setDrawerOpen(true);
	};

	const handleOpenEdit = (record: KnowledgeBase) => {
		setEditingKb(record);
		form.setFieldsValue({
			name: record.name,
			description: record.description,
			status: record.status,
		});
		setDrawerOpen(true);
	};

	const handleCopyId = (id: string) => {
		navigator.clipboard.writeText(id);
		message.success("ID 已复制到剪贴板");
	};

	const handleDelete = (kbId: string) => {
		deleteMutation.mutate(kbId);
	};

	const handleSubmit = async (values: Record<string, unknown>) => {
		try {
			if (editingKb) {
				const parsed = knowledgeBaseUpdateSchema.parse(values);
				await updateMutation.mutateAsync({
					kbId: editingKb.id,
					values: parsed,
				});
			} else {
				const parsed = knowledgeBaseCreateSchema.parse(values);
				await createMutation.mutateAsync(parsed);
			}
		} catch (err) {
			if (err instanceof z.ZodError) {
				message.error(err.issues[0].message);
			} else if (err instanceof Error) {
				message.error(err.message);
			}
		}
	};

	const knowledgeBases = useMemo(() => knowledgeQuery.data ?? [], [knowledgeQuery.data]);
	
	const filteredKbs = useMemo(() => {
		let result = knowledgeBases;

		if (!canAccessAdmin) {
			result = result.filter(kb => kb.status === 'ACTIVE');
		}

		if (filters.status !== 'ALL') {
			result = result.filter(kb => kb.status === filters.status);
		}

		if (filters.keyword.trim()) {
			const k = filters.keyword.trim().toLowerCase();
			result = result.filter(kb => 
				kb.name.toLowerCase().includes(k) || 
				kb.id.toLowerCase().includes(k)
			);
		}

		return result;
	}, [knowledgeBases, canAccessAdmin, filters]);

	const stats = useMemo(() => {
		const activeKbs = knowledgeBases.filter(kb => kb.status === 'ACTIVE');
		return {
			kbCount: knowledgeQuery.isLoading ? "--" : activeKbs.length,
			docTotal: knowledgeQuery.isLoading ? "--" : activeKbs.reduce((acc, kb) => acc + kb.indexedDocumentCount, 0)
		};
	}, [knowledgeBases, knowledgeQuery.isLoading]);

	const onFilterSubmit = (values: Record<string, string>) => {
		setFilters({
			keyword: values.keyword || "",
			status: values.status || "ALL",
		});
	};

	const onFilterReset = () => {
		filterForm.resetFields();
		setFilters({
			keyword: "",
			status: "ALL",
		});
	};

	return (
		<ConsolePageFrame
			eyebrow="Knowledge Base"
			title="知识库管理"
			description={
				<div className="knowledge-header-desc">
					<div className="knowledge-desc-text">管理和选择知识库以开启深度问答或查阅原始文档上下文。</div>
					<div className="knowledge-desc-metrics">
						<span className="metric-tag">
							<span className="label">活跃知识库:</span>
							<span className="value">{stats.kbCount}</span>
						</span>
						<span className="metric-tag">
							<span className="label">累计文档规模:</span>
							<span className="value">{stats.docTotal}</span>
						</span>
					</div>
				</div>
			}
			actions={
				canAccessAdmin ? (
					<Button 
						type="primary" 
						icon={<PlusOutlined />} 
						onClick={handleOpenCreate}
					>
						创建知识库
					</Button>
				) : null
			}
			summary={null}
			status={null}
		>
			<div className="knowledge-selection-box">
				<Card className="knowledge-filter-card" size="small">
					<Form
						form={filterForm}
						layout="inline"
						onFinish={onFilterSubmit}
						initialValues={{ status: "ALL" }}
					>
						<Form.Item name="keyword" label="搜索" style={{ minWidth: 260 }}>
							<Input 
								placeholder="名称或 ID" 
								prefix={<SearchOutlined style={{ color: 'var(--console-ink-faint)' }} />}
								allowClear
							/>
						</Form.Item>
						{canAccessAdmin && (
							<Form.Item name="status" label="状态" style={{ minWidth: 160 }}>
								<Select
									options={[
										{ label: "全部状态", value: "ALL" },
										{ label: "ACTIVE (激活)", value: "ACTIVE" },
										{ label: "INACTIVE (停用)", value: "INACTIVE" },
										{ label: "DELETED (已删除)", value: "DELETED" },
									]}
								/>
							</Form.Item>
						)}
						<Form.Item>
							<Space>
								<Button type="primary" htmlType="submit">查询</Button>
								<Button icon={<ReloadOutlined />} onClick={onFilterReset}>重置</Button>
							</Space>
						</Form.Item>
					</Form>
				</Card>

				<div className="knowledge-selection-grid">
					{knowledgeQuery.isLoading ? (
						<div style={{ padding: '60px 0', textAlign: 'center' }}>
							<Empty description="正在加载知识库目录..." />
						</div>
					) : filteredKbs.length === 0 ? (
						<Empty
							image={Empty.PRESENTED_IMAGE_SIMPLE}
							description={knowledgeBases.length === 0 ? "暂无可访问的知识库" : "未找到匹配的知识库"}
							className="ingest-empty-state"
						>
							{knowledgeBases.length === 0 && canAccessAdmin && (
								<Button type="link" onClick={handleOpenCreate}>立即初始化第一个知识库</Button>
							)}
						</Empty>
					) : (
						<Row gutter={[24, 24]}>
							{filteredKbs.map((kb) => (
								<Col xs={24} md={12} xl={8} key={kb.id}>
									<Card 
										hoverable={kb.status !== 'DELETED'}
										className={`knowledge-entry-card ${kb.status === 'INACTIVE' ? 'kb-inactive' : ''} ${kb.status === 'DELETED' ? 'kb-deleted' : ''}`}
										actions={[
											<Tooltip title={kb.status === 'ACTIVE' ? "进入问答" : `知识库${kb.status === 'DELETED' ? '已删除，禁止操作' : '已停用'}`} key="qa">
												<Button 
													type="text" 
													icon={<CommentOutlined />} 
													disabled={kb.status !== 'ACTIVE'}
													onClick={() => navigate(`/qa?kbId=${kb.id}`)}
												>
													进入问答
												</Button>
											</Tooltip>,
											<Tooltip title={kb.status === 'ACTIVE' ? "浏览文档" : `知识库${kb.status === 'DELETED' ? '已删除，禁止操作' : '已停用'}`} key="docs">
												<Button 
													type="text" 
													icon={<FileTextOutlined />} 
													disabled={kb.status !== 'ACTIVE'}
													onClick={() => navigate(`/ingest/documents?kbId=${kb.id}`)}
												>
													文档
												</Button>
											</Tooltip>,
											...(canAccessAdmin ? [
												<Tooltip title={kb.status === 'DELETED' ? "已删除，禁止操作" : "成员授权"} key="grant">
													<Button
														type="text"
														icon={<TeamOutlined />}
														disabled={kb.status === 'DELETED'}
														onClick={() => navigate(`/admin/knowledge-bases/${encodeURIComponent(kb.id)}/grants`)}
													>
														授权
													</Button>
												</Tooltip>,
												<Tooltip title={kb.status === 'DELETED' ? "已删除，禁止操作" : "治理配置"} key="setting">
													<Button 
														type="text" 
														icon={<SettingOutlined />} 
														disabled={kb.status === 'DELETED'}
														onClick={() => handleOpenEdit(kb)}
													>
														治理
													</Button>
												</Tooltip>,
												<Tooltip title={kb.status === 'DELETED' ? "已删除，禁止操作" : "删除知识库"} key="delete">
													<Button 
														type="text" 
														danger
														icon={<DeleteOutlined />} 
														disabled={kb.status === 'DELETED'}
														onClick={() => setDeleteTarget(kb)}
													/>
												</Tooltip>
											] : [])
										]}
									>
										<div className="knowledge-card-content">
											<div className="knowledge-card-head">
												<div className="knowledge-card-icon">
													<DatabaseOutlined />
												</div>
												<div className="knowledge-card-meta">
													<div className="knowledge-card-title-row">
														<Title level={4} className="knowledge-card-title">{kb.name}</Title>
														{kb.status !== 'ACTIVE' && <KnowledgeBaseStatusTag status={kb.status} />}
													</div>
													<div className="knowledge-card-id-wrapper" onClick={() => handleCopyId(kb.id)}>
														<Text className="knowledge-card-id">ID: {kb.id}</Text>
														<CopyOutlined className="id-copy-icon" />
													</div>
												</div>
											</div>
											<Paragraph className="knowledge-card-desc">
												{kb.description || "暂无描述信息"}
											</Paragraph>
											<div className="knowledge-card-stats">
												<div className="knowledge-stat-item">
													<Text className="knowledge-stat-value">{kb.indexedDocumentCount}</Text>
													<Text className="knowledge-stat-label">DOCS</Text>
												</div>
											</div>
										</div>
									</Card>
								</Col>
							))}
						</Row>
					)}
				</div>
			</div>

			<Drawer
				title={editingKb ? "编辑知识库配置" : "创建新知识库"}
				width={480}
				open={drawerOpen}
				onClose={() => setDrawerOpen(false)}
				destroyOnClose
				extra={
					<Space>
						<Button onClick={() => setDrawerOpen(false)}>取消</Button>
						<Button 
							type="primary" 
							onClick={() => form.submit()}
							loading={createMutation.isPending || updateMutation.isPending}
						>
							确认提交
						</Button>
					</Space>
				}
			>
				{knowledgeQuery.isError && <ApiErrorAlert error={knowledgeQuery.error} />}
				<KnowledgeBaseForm
					form={form}
					onFinish={handleSubmit}
				/>
			</Drawer>

			<DeleteKnowledgeBaseConfirmModal
				open={Boolean(deleteTarget)}
				knowledgeBase={deleteTarget}
				confirmLoading={deleteMutation.isPending}
				error={deleteMutation.error}
				onCancel={() => setDeleteTarget(null)}
				onConfirm={() => {
					if (deleteTarget) {
						handleDelete(deleteTarget.id);
					}
				}}
			/>
		</ConsolePageFrame>
	);
}
