import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
	BookOutlined,
	FileTextOutlined,
	FolderOpenOutlined,
	LinkOutlined,
	MessageOutlined,
	RobotOutlined,
	SendOutlined,
	UserOutlined,
} from "@ant-design/icons";
import {
	Avatar,
	Button,
	Card,
	Drawer,
	Empty,
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

function formatVersionLabel(versionNumber?: number | null): string {
	return typeof versionNumber === "number" ? `v${versionNumber}` : "未知版本";
}

function formatReferenceUpdatedAt(value?: string | null): string {
	if (!value) {
		return "未知更新时间";
	}
	const timestamp = Date.parse(value);
	if (Number.isNaN(timestamp)) {
		return value;
	}
	return new Intl.DateTimeFormat("zh-CN", {
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
	}).format(new Date(timestamp));
}

function isStaleReference(reference: AskReference): boolean {
	if (typeof reference.isLatestVersion === "boolean") {
		return !reference.isLatestVersion;
	}
	if (
		typeof reference.sourceVersionNumber === "number" &&
		typeof reference.latestVersionNumber === "number"
	) {
		return reference.sourceVersionNumber < reference.latestVersionNumber;
	}
	return false;
}

function buildReferenceReadPath(reference: AskReference): string | null {
	if (typeof reference.sourceVersionNumber !== "number") {
		return null;
	}
	return `/ingest/documents/${encodeURIComponent(reference.documentId)}/versions/${reference.sourceVersionNumber}/read?mode=single`;
}

function ReferenceChip({
	reference,
	onSelect,
}: {
	reference: AskReference;
	onSelect: (reference: AskReference) => void;
}) {
	const stale = isStaleReference(reference);

	return (
		<button
			type="button"
			className={stale ? "qa-citation-chip is-stale" : "qa-citation-chip"}
			onClick={() => onSelect(reference)}
			data-testid={`reference-card-${reference.documentId}-${reference.chunkIndex}`}
		>
			<div className="qa-citation-chip__head">
				<span className="qa-citation-chip__title">
					{reference.sourceFilename || reference.documentId}
				</span>
				<Tag color={stale ? "warning" : "blue"}>
					{formatVersionLabel(reference.sourceVersionNumber)}
				</Tag>
			</div>
			<div className="qa-citation-chip__preview">{reference.contentPreview}</div>
			<div className="qa-citation-chip__meta">
				<span>{reference.documentId}</span>
				<span>分块 #{reference.chunkIndex}</span>
				<span>{formatReferenceUpdatedAt(reference.sourceUpdatedAt)}</span>
			</div>
			<div className="qa-citation-chip__action">查看片段与问答基线文档</div>
		</button>
	);
}

export function QaPage() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const [form] = Form.useForm<{
		kbId: string;
		question: string;
		topK: number;
	}>();
	const [conversation, setConversation] = useState<QaConversationTurn[]>([]);
	const [activeReference, setActiveReference] = useState<AskReference | null>(null);
	const timelineRef = useRef<HTMLDivElement | null>(null);
	const selectedKnowledgeBaseId = Form.useWatch("kbId", form);

	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});
	const activeKnowledgeBases = useMemo(
		() => (knowledgeQuery.data ?? []).filter((item) => item.status === "ACTIVE"),
		[knowledgeQuery.data],
	);
	const selectedKnowledgeBase = useMemo(
		() =>
			activeKnowledgeBases.find((item) => item.id === selectedKnowledgeBaseId) ??
			null,
		[activeKnowledgeBases, selectedKnowledgeBaseId],
	);

	useEffect(() => {
		if (activeKnowledgeBases.length === 0) {
			return;
		}
		const currentKbId = form.getFieldValue("kbId");
		const queryKbId = searchParams.get("kbId");
		const lastKbId = localStorage.getItem("myai:lastKbId");
		const candidate = [
			currentKbId,
			queryKbId,
			lastKbId,
			"default",
			activeKnowledgeBases[0]?.id,
		].find((kbId) =>
			kbId ? activeKnowledgeBases.some((item) => item.id === kbId) : false,
		);
		if (candidate) {
			form.setFieldValue("kbId", candidate);
		}
	}, [activeKnowledgeBases, form, searchParams]);

	const askMutation = useMutation({
		mutationFn: (values: z.infer<typeof qaFormSchema>) =>
			askQuestion({
				question: values.question,
				kbId: values.kbId,
				topK: values.topK,
			}),
		onSuccess: (data, values) => {
			const kbName =
				activeKnowledgeBases.find((item) => item.id === values.kbId)?.name ??
				values.kbId;
			setConversation((current) => [
				...current,
				{
					id: `${Date.now()}-${current.length}`,
					kbId: values.kbId,
					kbName,
					question: values.question,
					answer: data.answer,
					references: data.references,
					staleReferences: data.staleReferences,
				},
			]);
			localStorage.setItem("myai:lastKbId", values.kbId);
			form.setFieldValue("question", "");
		},
	});

	useEffect(() => {
		if (!timelineRef.current) {
			return;
		}
		timelineRef.current.scrollTop = timelineRef.current.scrollHeight;
	}, [conversation, askMutation.isPending]);

	const onSubmit = async () => {
		const values = qaFormSchema.parse(form.getFieldsValue());
		await askMutation.mutateAsync(values);
	};

	const openReferenceBaseline = () => {
		if (!activeReference) {
			return;
		}
		const readPath = buildReferenceReadPath(activeReference);
		if (readPath) {
			navigate(readPath);
			return;
		}
		navigate(`/ingest/documents/${encodeURIComponent(activeReference.documentId)}`);
	};

	return (
		<div className="qa-chat-page">
			<div className="qa-chat-shell">
				<aside className="qa-chat-shell__sidebar" data-testid="qa-kb-sidebar">
					<div className="qa-sidebar__header">
						<div className="qa-sidebar__eyebrow">Knowledge</div>
						<Typography.Title level={4}>选择知识库</Typography.Title>
						<Typography.Paragraph type="secondary">
							先选定当前问答边界，再开始提问。
						</Typography.Paragraph>
					</div>

					{knowledgeQuery.isError ? (
						<ApiErrorAlert error={knowledgeQuery.error} />
					) : activeKnowledgeBases.length === 0 ? (
						<Empty
							image={Empty.PRESENTED_IMAGE_SIMPLE}
							description="当前没有可问答的知识库"
						/>
					) : (
						<div className="qa-kb-list">
							{activeKnowledgeBases.map((knowledgeBase) => {
								const active = knowledgeBase.id === selectedKnowledgeBaseId;
								return (
									<button
										key={knowledgeBase.id}
										type="button"
										className={active ? "qa-kb-item is-active" : "qa-kb-item"}
										onClick={() => {
											form.setFieldValue("kbId", knowledgeBase.id);
										}}
										data-testid={`qa-kb-${knowledgeBase.id}`}
									>
										<div className="qa-kb-item__icon">
											<FolderOpenOutlined />
										</div>
										<div className="qa-kb-item__body">
											<div className="qa-kb-item__name">{knowledgeBase.name}</div>
											<div className="qa-kb-item__desc">
												{knowledgeBase.description || "当前知识库未填写说明"}
											</div>
										</div>
										<Tag>{knowledgeBase.indexedDocumentCount} 篇</Tag>
									</button>
								);
							})}
						</div>
					)}
				</aside>

				<section className="qa-chat-shell__main">
					<header className="qa-chat-shell__header">
						<div>
							<div className="qa-chat-shell__eyebrow">智能问答</div>
							<Typography.Title level={3}>只看问答与引用</Typography.Title>
						</div>
						<div className="qa-chat-shell__context" data-testid="qa-context-strip">
							<BookOutlined />
							<span>
								正在查询：
								<strong>
									{selectedKnowledgeBase?.name ?? "请选择 knowledge base"}
								</strong>
							</span>
						</div>
					</header>

					<div
						className="qa-chat-shell__timeline"
						ref={timelineRef}
						data-testid="qa-chat-list"
					>
						{conversation.length === 0 ? (
							<div className="qa-empty-state">
								<div className="qa-empty-state__icon">
									<MessageOutlined />
								</div>
								<Typography.Title level={4}>开始一轮新问答</Typography.Title>
								<Typography.Paragraph type="secondary">
									页面只保留问题、答案、引用片段和问答基线文档入口。
								</Typography.Paragraph>
							</div>
						) : (
							conversation.map((turn) => (
								<div key={turn.id} className="qa-turn" data-testid="qa-turn">
									<div className="qa-message qa-message--user">
										<Avatar
											size={42}
											className="qa-message__avatar qa-message__avatar--user"
											icon={<UserOutlined />}
										/>
										<div className="qa-message__bubble qa-message__bubble--user">
											<div className="qa-message__kb">来自 {turn.kbName}</div>
											<div className="qa-message__text">{turn.question}</div>
										</div>
									</div>

									<div className="qa-message qa-message--assistant">
										<Avatar
											size={42}
											className="qa-message__avatar qa-message__avatar--assistant"
											icon={<RobotOutlined />}
										/>
										<div className="qa-message__bubble qa-message__bubble--assistant">
											<div className="qa-message__text qa-message__text--answer">
												{turn.answer}
											</div>

											{turn.staleReferences?.hasStaleReferences && (
												<div
													className="qa-message__stale-note"
													data-testid="qa-stale-reference-banner"
												>
													包含 {turn.staleReferences.staleReferenceCount} 条非最新版本引用，已按当前问答基线返回。
												</div>
											)}

											<div className="qa-sources">
												<div className="qa-sources__title">
													<FileTextOutlined />
													<span>引用片段</span>
												</div>
												{turn.references.length > 0 ? (
													<div className="qa-citation-grid" data-testid="reference-list">
														{turn.references.map((reference) => (
															<ReferenceChip
																key={`${reference.documentId}-${reference.chunkIndex}`}
																reference={reference}
																onSelect={setActiveReference}
															/>
														))}
													</div>
												) : (
													<div className="qa-sources__empty">
														当前回答没有命中文档引用，内容来自模型兜底。
													</div>
												)}
											</div>
										</div>
									</div>
								</div>
							))
						)}
					</div>

					<Card className="qa-chat-shell__composer" bodyStyle={{ padding: 18 }}>
						{askMutation.isError && <ApiErrorAlert error={askMutation.error} />}
						<Form
							form={form}
							layout="vertical"
							initialValues={{
								kbId: "default",
								question: "",
								topK: 5,
							}}
							onFinish={onSubmit}
						>
							<Form.Item name="kbId" hidden>
								<Input />
							</Form.Item>

							<div className="qa-composer__controls">
								<Form.Item name="topK" label="引用范围" className="qa-composer__topk">
									<InputNumber min={1} max={20} style={{ width: 140 }} />
								</Form.Item>
							</div>

							<div className="qa-composer__row">
								<Form.Item name="question" className="qa-composer__input">
									<Input.TextArea
										placeholder="请输入您的问题..."
										autoSize={{ minRows: 2, maxRows: 5 }}
										maxLength={2000}
										showCount
										disabled={!selectedKnowledgeBase}
									/>
								</Form.Item>
								<Button
									type="primary"
									htmlType="submit"
									size="large"
									icon={<SendOutlined />}
									loading={askMutation.isPending}
									disabled={!selectedKnowledgeBase}
								>
									发送
								</Button>
							</div>
						</Form>
					</Card>
				</section>
			</div>

			<Drawer
				open={activeReference !== null}
				onClose={() => setActiveReference(null)}
				width={420}
				title={activeReference?.sourceFilename || "引用片段"}
			>
				{activeReference && (
					<div className="qa-reference-drawer" data-testid="qa-reference-drawer">
						<Space wrap>
							<Tag color="blue">{formatVersionLabel(activeReference.sourceVersionNumber)}</Tag>
							{isStaleReference(activeReference) && (
								<Tag color="warning">
									当前最新版本 {formatVersionLabel(activeReference.latestVersionNumber)}
								</Tag>
							)}
							<Tag>{activeReference.documentId}</Tag>
						</Space>

						<div className="qa-reference-drawer__meta">
							<span>分块 #{activeReference.chunkIndex}</span>
							<span>{formatReferenceUpdatedAt(activeReference.sourceUpdatedAt)}</span>
						</div>

						<div className="qa-reference-drawer__snippet">
							{activeReference.contentPreview}
						</div>

						<div className="qa-reference-drawer__actions">
							<Button
								type="primary"
								icon={<BookOutlined />}
								onClick={openReferenceBaseline}
								data-testid="qa-open-baseline-doc"
							>
								查看问答基线文档
							</Button>
							<Button
								icon={<LinkOutlined />}
								onClick={() =>
									navigate(
										`/ingest/documents/${encodeURIComponent(activeReference.documentId)}`,
									)
								}
							>
								查看文档详情
							</Button>
						</div>
					</div>
				)}
			</Drawer>
		</div>
	);
}
