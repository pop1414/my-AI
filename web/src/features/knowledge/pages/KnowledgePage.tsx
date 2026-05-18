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
} from "antd";
import {
	PlusOutlined,
	DatabaseOutlined,
	CommentOutlined,
	FileTextOutlined,
	SettingOutlined,
	CopyOutlined,
} from "@ant-design/icons";
import { z } from "zod";
import {
	createKnowledgeBase,
	listKnowledgeBases,
	type KnowledgeBase,
	updateKnowledgeBase,
} from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { useAuth } from "../../../shared/auth/AuthContext";
import {
	ConsolePageFrame,
} from "../../../shared/ui/console/ConsolePageFrame";

import { KnowledgeBaseForm } from "../components/KnowledgeBaseForm";
import { KnowledgeBaseStatusTag } from "../components/KnowledgeBaseStatusTag";
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
	const [drawerOpen, setDrawerOpen] = useState(false);
	const [editingKb, setEditingKb] = useState<KnowledgeBase | null>(null);

	const canAccessAdmin = Boolean(user?.capabilities.canAccessAdmin);

	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
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
			values: any;
		}) => updateKnowledgeBase(kbId, values),
		onSuccess: () => {
			setDrawerOpen(false);
			setEditingKb(null);
			form.resetFields();
			queryClient.invalidateQueries({ queryKey: ["knowledge-bases"] });
			message.success("知识库配置已更新");
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

	const handleSubmit = async (values: any) => {
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

	const knowledgeBases = knowledgeQuery.data ?? [];
	const visibleKbs = canAccessAdmin 
		? knowledgeBases 
		: knowledgeBases.filter(kb => kb.status === 'ACTIVE');

	const stats = useMemo(() => {
		const activeKbs = knowledgeBases.filter(kb => kb.status === 'ACTIVE');
		return {
			kbCount: knowledgeQuery.isLoading ? "--" : activeKbs.length,
			docTotal: knowledgeQuery.isLoading ? "--" : activeKbs.reduce((acc, kb) => acc + kb.indexedDocumentCount, 0)
		};
	}, [knowledgeBases, knowledgeQuery.isLoading]);

	return (
		<ConsolePageFrame
			eyebrow="Knowledge Base"
			title="选择知识库"
			description={
				<div className="knowledge-header-desc">
					<div className="knowledge-desc-text">选择目标知识库以开启深度问答或查阅原始文档上下文。</div>
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
				<div className="knowledge-selection-grid">
					{knowledgeQuery.isLoading ? (
						<div style={{ padding: '60px 0', textAlign: 'center' }}>
							<Empty description="正在加载知识库目录..." />
						</div>
					) : visibleKbs.length === 0 ? (
						<Empty
							image={Empty.PRESENTED_IMAGE_SIMPLE}
							description="暂无可访问的知识库"
							className="ingest-empty-state"
						>
							{canAccessAdmin && <Button type="link" onClick={handleOpenCreate}>立即初始化第一个知识库</Button>}
						</Empty>
					) : (
						<Row gutter={[24, 24]}>
							{visibleKbs.map((kb) => (
								<Col xs={24} md={12} xl={8} key={kb.id}>
									<Card 
										hoverable 
										className={`knowledge-entry-card ${kb.status === 'INACTIVE' ? 'kb-inactive' : ''}`}
										actions={[
											<Tooltip title={kb.status === 'ACTIVE' ? "进入问答" : "知识库已停用"} key="qa">
												<Button 
													type="text" 
													icon={<CommentOutlined />} 
													disabled={kb.status !== 'ACTIVE'}
													onClick={() => navigate(`/qa?kbId=${kb.id}`)}
												>
													进入问答
												</Button>
											</Tooltip>,
											<Tooltip title={kb.status === 'ACTIVE' ? "浏览文档" : "知识库已停用"} key="docs">
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
												<Tooltip title="治理配置" key="setting">
													<Button 
														type="text" 
														icon={<SettingOutlined />} 
														onClick={() => handleOpenEdit(kb)}
													>
														治理
													</Button>
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
														{kb.status === 'INACTIVE' && <KnowledgeBaseStatusTag status={kb.status} />}
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
		</ConsolePageFrame>
	);
}
