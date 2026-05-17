import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
	BookOutlined,
	DatabaseOutlined,
	FileSearchOutlined,
	LinkOutlined,
	MessageOutlined,
	SendOutlined,
	SettingOutlined,
} from "@ant-design/icons";
import {
	Button,
	Card,
	Form,
	Input,
	InputNumber,
	Space,
	Tag,
	Typography,
} from "antd";
import { useNavigate, useSearchParams } from "react-router-dom";
import { z } from "zod";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import {
	askQuestion,
	type AskReference,
	type AskResponse,
} from "../../../shared/api/qaApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import { QaSidebar } from "../components/QaSidebar";
import { QaTurn } from "../components/QaTurn";
import "./QaPage.css";

const qaFormSchema = z.object({
	kbId: z.string().trim().min(1, "请选择知识库"),
	question: z.string().trim().min(1, "请输入问题"),
	topK: z.number().int().min(1, "topK 最小为 1").max(20, "topK 最大为 20"),
});

type QaConversationTurn = {
	id: string;
	kbId: string;
	kbName: string;
	question: string;
	answer: string;
	references: AskReference[];
	staleReferences: AskResponse["staleReferences"];
};

type PanelMode = "overview" | "reference" | "settings";

function formatVersionLabel(versionNumber?: number | null): string {
	return typeof versionNumber === "number" ? `v${versionNumber}` : "未知版本";
}

function buildReferenceKey(reference: AskReference): string {
	return `${reference.documentId}-${reference.chunkIndex}`;
}

export function QaPage() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const [form] = Form.useForm();
	const [conversation, setConversation] = useState<QaConversationTurn[]>([]);
	const [panelMode, setPanelMode] = useState<PanelMode>("overview");
	const [activeReference, setActiveReference] = useState<AskReference | null>(null);
	const timelineRef = useRef<HTMLDivElement | null>(null);
	const selectedKnowledgeBaseId = Form.useWatch("kbId", form);
	const topKValue = Form.useWatch("topK", form) ?? 5;

	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});
	const activeKnowledgeBases = useMemo(
		() => (knowledgeQuery.data ?? []).filter((item) => item.status === "ACTIVE"),
		[knowledgeQuery.data],
	);
	const selectedKnowledgeBase = useMemo(
		() => activeKnowledgeBases.find((item) => item.id === selectedKnowledgeBaseId) ?? null,
		[activeKnowledgeBases, selectedKnowledgeBaseId],
	);

	useEffect(() => {
		if (activeKnowledgeBases.length === 0) return;
		const queryKbId = searchParams.get("kbId");
		const lastKbId = localStorage.getItem("myai:lastKbId");
		const candidate = [queryKbId, lastKbId, activeKnowledgeBases[0]?.id].find(
			(id) => id && activeKnowledgeBases.some((kb) => kb.id === id),
		);
		if (candidate) form.setFieldValue("kbId", candidate);
	}, [activeKnowledgeBases, form, searchParams]);

	const askMutation = useMutation({
		mutationFn: (values: z.infer<typeof qaFormSchema>) =>
			askQuestion({ question: values.question, kbId: values.kbId, topK: values.topK }),
		onSuccess: (data, values) => {
			const kbName = activeKnowledgeBases.find((item) => item.id === values.kbId)?.name ?? values.kbId;
			const nextTurn: QaConversationTurn = {
				id: `${Date.now()}`,
				kbId: values.kbId,
				kbName,
				question: values.question,
				answer: data.answer,
				references: data.references,
				staleReferences: data.staleReferences,
			};
			setConversation((current) => [...current, nextTurn]);
			localStorage.setItem("myai:lastKbId", values.kbId);
			form.setFieldValue("question", "");
			if (data.references.length > 0) {
				setActiveReference(data.references[0] ?? null);
				setPanelMode("reference");
			}
		},
	});

	useEffect(() => {
		if (timelineRef.current) {
			timelineRef.current.scrollTop = timelineRef.current.scrollHeight;
		}
	}, [conversation, askMutation.isPending]);

	return (
		<div className="qa-rag-page">
			<div className="qa-rag-shell">
				<QaSidebar
					loading={knowledgeQuery.isLoading}
					knowledgeBases={activeKnowledgeBases}
					selectedId={selectedKnowledgeBaseId}
					onSelect={(id) => {
						form.setFieldValue("kbId", id);
						setPanelMode("overview");
					}}
				/>

				<section className="qa-rag-shell__chat">
					<header className="qa-chat-header">
						<div className="qa-rag-shell__section-head" style={{ padding: 0 }}>
							<div className="qa-rag-shell__eyebrow">Dialogue</div>
							<Typography.Title level={4} style={{ margin: 0 }}>对话</Typography.Title>
						</div>
						<div className="qa-chat-header__context">
							<BookOutlined />
							<span>
								当前知识库：<strong>{selectedKnowledgeBase?.name ?? "未选择"}</strong>
							</span>
						</div>
					</header>

					<div className="qa-chat-timeline" ref={timelineRef}>
						<div className="qa-chat-timeline__container">
							{conversation.length === 0 ? (
								<div style={{ textAlign: 'center', marginTop: 100 }}>
									<MessageOutlined style={{ fontSize: 40, color: 'var(--console-border-strong)', marginBottom: 20 }} />
									<Typography.Title level={4}>开始新对话</Typography.Title>
									<Typography.Paragraph type="secondary">
										选择左侧知识库并输入您的问题，右侧将实时展示 AI 的检索依据。
									</Typography.Paragraph>
								</div>
							) : (
								conversation.map((turn) => (
									<QaTurn
										key={turn.id}
										turn={turn}
										activeReferenceKey={activeReference ? buildReferenceKey(activeReference) : ""}
										onReferenceSelect={(ref) => {
											setActiveReference(ref);
											setPanelMode("reference");
										}}
									/>
								))
							)}
							{askMutation.isPending && (
								<div className="qa-turn">
									<Typography.Text type="secondary">AI 正在处理您的提问...</Typography.Text>
								</div>
							)}
						</div>
					</div>

					<div className="qa-chat-composer">
						<div className="qa-chat-composer__container">
							{askMutation.isError && <ApiErrorAlert error={askMutation.error} style={{ marginBottom: 16 }} />}
							<Form form={form} onFinish={() => askMutation.mutate(qaFormSchema.parse(form.getFieldsValue()))} initialValues={{ topK: 5 }}>
								<Form.Item name="kbId" hidden><Input /></Form.Item>
								<Form.Item name="topK" hidden><InputNumber /></Form.Item>
								<div className="qa-chat-composer__box">
									<Form.Item name="question" noStyle>
										<Input.TextArea
											className="qa-chat-composer__input"
											placeholder="向 AI 提问..."
											autoSize={{ minRows: 1, maxRows: 6 }}
											onPressEnter={(e) => {
												if (!e.shiftKey) {
													e.preventDefault();
													form.submit();
												}
											}}
										/>
									</Form.Item>
									<div className="qa-chat-composer__actions">
										<Button
											type="primary"
											htmlType="submit"
											icon={<SendOutlined />}
											loading={askMutation.isPending}
											disabled={!selectedKnowledgeBaseId}
										>
											发送
										</Button>
									</div>
								</div>
							</Form>
						</div>
					</div>
				</section>

				<aside className="qa-rag-shell__panel">
					<div className="qa-panel-header">
						<div className="qa-rag-shell__section-head">
							<div className="qa-rag-shell__eyebrow">Evidence</div>
							<Typography.Title level={4} style={{ margin: 0 }}>证据查验</Typography.Title>
						</div>
						<div className="qa-panel-header__tabs">
							<button className={`qa-panel-tab ${panelMode === 'overview' ? 'is-active' : ''}`} onClick={() => setPanelMode('overview')}>
								<DatabaseOutlined /> 概览
							</button>
							<button className={`qa-panel-tab ${panelMode === 'reference' ? 'is-active' : ''}`} onClick={() => setPanelMode('reference')} disabled={!activeReference}>
								<FileSearchOutlined /> 证据
							</button>
							<button className={`qa-panel-tab ${panelMode === 'settings' ? 'is-active' : ''}`} onClick={() => setPanelMode('settings')}>
								<SettingOutlined /> 参数
							</button>
						</div>
					</div>

					<div className="qa-panel-body">
						{panelMode === "overview" && (
							<div className="qa-panel-stack">
								<div className="qa-stat-grid">
									<div className="qa-stat-card">
										<div className="qa-stat-card__label">索引文档</div>
										<div className="qa-stat-card__value">{selectedKnowledgeBase?.indexedDocumentCount ?? 0}</div>
									</div>
									<div className="qa-stat-card">
										<div className="qa-stat-card__label">命中片段</div>
										<div className="qa-stat-card__value">{activeReference ? '5' : '0'}</div>
									</div>
									<div className="qa-stat-card">
										<div className="qa-stat-card__label">模型</div>
										<div className="qa-stat-card__value">GPT-4o</div>
									</div>
								</div>
								<Card size="small" title="知识库说明" bordered={false} style={{ background: '#fafafa' }}>
									<Typography.Text type="secondary">{selectedKnowledgeBase?.description || "暂无描述"}</Typography.Text>
								</Card>
							</div>
						)}

						{panelMode === "reference" && activeReference && (
							<div className="qa-panel-stack">
								<div className="qa-evidence-card">
									<Typography.Title level={5} style={{ margin: 0 }}>
										{activeReference.sourceFilename || "未知文档"}
									</Typography.Title>
									<Space style={{ marginTop: 8 }}>
										<Tag bordered={false}>{formatVersionLabel(activeReference.sourceVersionNumber)}</Tag>
										<Typography.Text type="secondary" style={{ fontSize: 12 }}>
											Chunk #{activeReference.chunkIndex}
										</Typography.Text>
									</Space>
									
									<div className="qa-document-preview" style={{ marginTop: 16 }}>
										<div className="qa-document-preview__toolbar">
											<span>Original Snippet</span>
											<span>Match Score: 0.92</span>
										</div>
										<div className="qa-document-preview__page">
											<mark>{activeReference.contentPreview}</mark>
										</div>
									</div>

									<div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
										<Button block onClick={() => navigate(`/ingest/documents/${activeReference.documentId}`)}>详情</Button>
										<Button block type="primary" icon={<LinkOutlined />}>定位原文</Button>
									</div>
								</div>
							</div>
						)}

						{panelMode === "settings" && (
							<div className="qa-panel-stack">
								<Typography.Text strong>RAG 配置</Typography.Text>
								<div style={{ padding: '12px', border: '1px solid var(--console-border)', borderRadius: 8 }}>
									<div style={{ marginBottom: 12 }}>
										<div style={{ fontSize: 12, color: 'var(--console-muted)', marginBottom: 4 }}>Top-K (检索条数)</div>
										<InputNumber min={1} max={20} value={topKValue} onChange={(v) => form.setFieldValue('topK', v)} style={{ width: '100%' }} />
									</div>
									<div>
										<div style={{ fontSize: 12, color: 'var(--console-muted)', marginBottom: 4 }}>Temperature (随机性)</div>
										<Input disabled value="0.7 (默认)" style={{ width: '100%' }} />
									</div>
								</div>
							</div>
						)}
					</div>
				</aside>
			</div>
		</div>
	);
}
