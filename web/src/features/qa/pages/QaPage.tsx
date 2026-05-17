import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
	BookOutlined,
	DatabaseOutlined,
	FileSearchOutlined,
	FolderOpenOutlined,
	LinkOutlined,
	MessageOutlined,
	RobotOutlined,
	SendOutlined,
	SettingOutlined,
	UserOutlined,
} from "@ant-design/icons";
import {
	Avatar,
	Button,
	Card,
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
import { listKnowledgeBases, type KnowledgeBase } from "../../../shared/api/knowledgeApi";
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

type PanelMode = "overview" | "reference" | "settings";
type ReferenceFeedback = "irrelevant" | "relevant";

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

function buildReferenceKey(reference: AskReference): string {
	return `${reference.documentId}-${reference.chunkIndex}`;
}

function buildReferenceReadPath(reference: AskReference): string | null {
	if (typeof reference.sourceVersionNumber !== "number") {
		return null;
	}
	return `/ingest/documents/${encodeURIComponent(reference.documentId)}/versions/${reference.sourceVersionNumber}/read?mode=single`;
}

function estimateKnowledgeBaseChars(
	knowledgeBase: KnowledgeBase | null,
	currentTurn: QaConversationTurn | null,
): string {
	if (!knowledgeBase) {
		return "--";
	}
	if (!currentTurn || currentTurn.kbId !== knowledgeBase.id) {
		return "待接入";
	}
	const contentChars = currentTurn.references.reduce(
		(total, item) => total + item.contentPreview.length,
		0,
	);
	return `${contentChars.toLocaleString("zh-CN")}+`;
}

function buildKnowledgeSummary(
	knowledgeBase: KnowledgeBase | null,
	currentTurn: QaConversationTurn | null,
): string[] {
	if (!knowledgeBase) {
		return ["请先在左侧选定 knowledge base，右侧证据面板会围绕该边界展开。"];
	}

	const summary = [
		knowledgeBase.description || "当前知识库未填写描述，可在知识库管理页补充业务范围说明。",
		`当前知识库包含 ${knowledgeBase.indexedDocumentCount} 篇已索引文档，所有问答都限定在该边界内检索。`,
	];

	if (!currentTurn || currentTurn.kbId !== knowledgeBase.id) {
		summary.push("发起一轮提问后，右侧会出现最近一次检索命中的引用片段与基线文档入口。");
		return summary;
	}

	summary.push(
		currentTurn.references.length > 0
			? `最近一轮问答命中了 ${currentTurn.references.length} 条引用，可在下方检索查验区逐条查看。`
			: "最近一轮问答没有命中文档引用，当前回答来自模型兜底。",
	);

	if (currentTurn.staleReferences?.hasStaleReferences) {
		summary.push(
			`其中 ${currentTurn.staleReferences.staleReferenceCount} 条引用不是最新版本，系统仍按当前 askable baseline 返回。`,
		);
	}

	return summary;
}

function buildPreviewParagraphs(reference: AskReference): string[] {
	return [
		"该面板用于核对 AI 回答依据。当前前端先展示命中的检索片段与版本信息，后续可继续升级为页码级原文定位。",
		reference.contentPreview,
		typeof reference.latestVersionNumber === "number" &&
		typeof reference.sourceVersionNumber === "number" &&
		reference.latestVersionNumber > reference.sourceVersionNumber
			? `当前引用来自 ${formatVersionLabel(reference.sourceVersionNumber)}，但该 document 的最新版本已经推进到 ${formatVersionLabel(reference.latestVersionNumber)}。`
			: `当前引用来自 ${formatVersionLabel(reference.sourceVersionNumber)}，该版本就是当前问答基线。`,
	];
}

function CitationTag({
	index,
	reference,
	active,
	onSelect,
}: {
	index: number;
	reference: AskReference;
	active: boolean;
	onSelect: (reference: AskReference) => void;
}) {
	return (
		<button
			type="button"
			className={active ? "qa-citation-tag is-active" : "qa-citation-tag"}
			onClick={() => onSelect(reference)}
			data-testid={`reference-card-${reference.documentId}-${reference.chunkIndex}`}
		>
			<span className="qa-citation-tag__index">[{index + 1}]</span>
			<span className="qa-citation-tag__label">
				{reference.sourceFilename || reference.documentId}
			</span>
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
	const [panelMode, setPanelMode] = useState<PanelMode>("overview");
	const [activeReference, setActiveReference] = useState<AskReference | null>(null);
	const [feedbackByReference, setFeedbackByReference] = useState<
		Record<string, ReferenceFeedback | undefined>
	>({});
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
		() =>
			activeKnowledgeBases.find((item) => item.id === selectedKnowledgeBaseId) ??
			null,
		[activeKnowledgeBases, selectedKnowledgeBaseId],
	);
	const currentTurn = conversation.at(-1) ?? null;
	const currentReferences = currentTurn?.references ?? [];
	const kbSummary = useMemo(
		() => buildKnowledgeSummary(selectedKnowledgeBase, currentTurn),
		[selectedKnowledgeBase, currentTurn],
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
			const nextTurn: QaConversationTurn = {
				id: `${Date.now()}-${values.kbId}`,
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
			} else {
				setActiveReference(null);
				setPanelMode("overview");
			}
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

	const selectReference = (reference: AskReference) => {
		setActiveReference(reference);
		setPanelMode("reference");
	};

	return (
		<div className="qa-rag-page">
			<div className="qa-rag-shell">
				<aside className="qa-rag-shell__sources" data-testid="qa-kb-sidebar">
					<div className="qa-rag-shell__section-head">
						<div className="qa-rag-shell__eyebrow">Sources</div>
						<Typography.Title level={4}>来源</Typography.Title>
					</div>

					{knowledgeQuery.isError ? (
						<ApiErrorAlert error={knowledgeQuery.error} />
					) : activeKnowledgeBases.length === 0 ? (
						<Empty
							image={Empty.PRESENTED_IMAGE_SIMPLE}
							description="当前没有可问答的知识库"
						/>
					) : (
						<div className="qa-source-list">
							{activeKnowledgeBases.map((knowledgeBase) => {
								const active = knowledgeBase.id === selectedKnowledgeBaseId;
								return (
									<button
										key={knowledgeBase.id}
										type="button"
										className={active ? "qa-source-item is-active" : "qa-source-item"}
										onClick={() => {
											form.setFieldValue("kbId", knowledgeBase.id);
											setPanelMode("overview");
										}}
										data-testid={`qa-kb-${knowledgeBase.id}`}
									>
										<div className="qa-source-item__icon">
											<FolderOpenOutlined />
										</div>
										<div className="qa-source-item__copy">
											<div className="qa-source-item__name">{knowledgeBase.name}</div>
											<div className="qa-source-item__meta">
												{knowledgeBase.indexedDocumentCount} 篇已索引文档
											</div>
										</div>
									</button>
								);
							})}
						</div>
					)}
				</aside>

				<section className="qa-rag-shell__chat">
					<header className="qa-chat-header">
						<div className="qa-rag-shell__section-head">
							<div className="qa-rag-shell__eyebrow">Dialogue</div>
							<Typography.Title level={4}>对话</Typography.Title>
						</div>
						<div className="qa-chat-header__context" data-testid="qa-context-strip">
							<BookOutlined />
							<span>
								当前知识库：
								<strong>{selectedKnowledgeBase?.name ?? "请选择 knowledge base"}</strong>
							</span>
						</div>
					</header>

					<div
						className="qa-chat-timeline"
						ref={timelineRef}
						data-testid="qa-chat-list"
					>
						{conversation.length === 0 ? (
							<div className="qa-chat-empty">
								<div className="qa-chat-empty__icon">
									<MessageOutlined />
								</div>
								<Typography.Title level={4}>开始提问</Typography.Title>
								<Typography.Paragraph type="secondary">
									中间只保留问题与回答，所有证据查验动作放到右侧常驻面板。
								</Typography.Paragraph>
							</div>
						) : (
							conversation.map((turn) => (
								<div key={turn.id} className="qa-turn" data-testid="qa-turn">
									<div className="qa-bubble-row qa-bubble-row--user">
										<Avatar
											size={40}
											className="qa-bubble-row__avatar qa-bubble-row__avatar--user"
											icon={<UserOutlined />}
										/>
										<div className="qa-bubble qa-bubble--user">
											<div className="qa-bubble__meta">提问于 {turn.kbName}</div>
											<div className="qa-bubble__content">{turn.question}</div>
										</div>
									</div>

									<div className="qa-bubble-row qa-bubble-row--assistant">
										<Avatar
											size={40}
											className="qa-bubble-row__avatar qa-bubble-row__avatar--assistant"
											icon={<RobotOutlined />}
										/>
										<div className="qa-bubble qa-bubble--assistant">
											<div className="qa-bubble__content qa-bubble__content--answer">
												{turn.answer}
											</div>

											{turn.staleReferences?.hasStaleReferences && (
												<div
													className="qa-bubble__warning"
													data-testid="qa-stale-reference-banner"
												>
													包含 {turn.staleReferences.staleReferenceCount} 条非最新版本引用，系统仍按当前 askable baseline 返回。
												</div>
											)}

											<div className="qa-citation-strip">
												<div className="qa-citation-strip__title">引用</div>
												{turn.references.length > 0 ? (
													<div className="qa-citation-strip__list" data-testid="reference-list">
														{turn.references.map((reference, index) => (
															<CitationTag
																key={buildReferenceKey(reference)}
																index={index}
																reference={reference}
																active={
																	buildReferenceKey(reference) ===
																	buildReferenceKey(activeReference ?? reference)
																}
																onSelect={selectReference}
															/>
														))}
													</div>
												) : (
													<div className="qa-citation-strip__empty">
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

					<Card className="qa-chat-composer" bodyStyle={{ padding: 18 }}>
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
							<Form.Item name="topK" hidden>
								<InputNumber />
							</Form.Item>

							<div className="qa-chat-composer__row">
								<Form.Item name="question" className="qa-chat-composer__input">
									<Input.TextArea
										placeholder="提问或继续追问"
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

				<aside className="qa-rag-shell__panel" data-testid="qa-context-panel">
					<div className="qa-panel-header">
						<div className="qa-rag-shell__section-head">
							<div className="qa-rag-shell__eyebrow">Evidence</div>
							<Typography.Title level={4}>证据查验</Typography.Title>
						</div>
						<div className="qa-panel-header__actions">
							<button
								type="button"
								className={panelMode === "overview" ? "qa-panel-tab is-active" : "qa-panel-tab"}
								onClick={() => setPanelMode("overview")}
							>
								<DatabaseOutlined />
								<span>概览</span>
							</button>
							<button
								type="button"
								className={panelMode === "reference" ? "qa-panel-tab is-active" : "qa-panel-tab"}
								onClick={() => setPanelMode("reference")}
								disabled={!activeReference}
							>
								<FileSearchOutlined />
								<span>证据</span>
							</button>
							<button
								type="button"
								className={panelMode === "settings" ? "qa-panel-tab is-active" : "qa-panel-tab"}
								onClick={() => setPanelMode("settings")}
							>
								<SettingOutlined />
								<span>参数</span>
							</button>
						</div>
					</div>

					<div className="qa-panel-body">
						{panelMode === "overview" && (
							<div className="qa-panel-stack">
								<div className="qa-stats-grid">
									<Card size="small" className="qa-stat-card">
										<div className="qa-stat-card__label">已索引文档</div>
										<div className="qa-stat-card__value">
											{selectedKnowledgeBase?.indexedDocumentCount ?? "--"}
										</div>
									</Card>
									<Card size="small" className="qa-stat-card">
										<div className="qa-stat-card__label">总字符</div>
										<div className="qa-stat-card__value">
											{estimateKnowledgeBaseChars(selectedKnowledgeBase, currentTurn)}
										</div>
									</Card>
									<Card size="small" className="qa-stat-card">
										<div className="qa-stat-card__label">最近命中片段</div>
										<div className="qa-stat-card__value">
											{currentReferences.length || "--"}
										</div>
									</Card>
								</div>

								<Card size="small" className="qa-panel-card">
									<Typography.Text strong>知识库摘要</Typography.Text>
									<div className="qa-outline-list">
										{kbSummary.map((item) => (
											<div key={item} className="qa-outline-list__item">
												{item}
											</div>
										))}
									</div>
								</Card>

								<Card size="small" className="qa-panel-card">
									<Typography.Text strong>检索查验</Typography.Text>
									<div className="qa-inspector-list">
										{currentReferences.length > 0 ? (
											currentReferences.map((reference, index) => {
												const referenceKey = buildReferenceKey(reference);
												const feedback = feedbackByReference[referenceKey];
												return (
													<div
														key={referenceKey}
														className="qa-inspector-item"
														role="button"
														tabIndex={0}
														onClick={() => selectReference(reference)}
														onKeyDown={(event) => {
															if (event.key === "Enter" || event.key === " ") {
																event.preventDefault();
																selectReference(reference);
															}
														}}
													>
														<div className="qa-inspector-item__head">
															<span className="qa-inspector-item__rank">
																#{index + 1}
															</span>
															<span className="qa-inspector-item__title">
																{reference.sourceFilename || reference.documentId}
															</span>
														</div>
														<div className="qa-inspector-item__preview">
															{reference.contentPreview}
														</div>
														<div className="qa-inspector-item__meta">
															<span>{formatVersionLabel(reference.sourceVersionNumber)}</span>
															<span>{formatReferenceUpdatedAt(reference.sourceUpdatedAt)}</span>
														</div>
														<div className="qa-inspector-item__feedback">
															<button
																type="button"
																className={
																	feedback === "irrelevant"
																		? "qa-feedback-button is-active"
																		: "qa-feedback-button"
																}
																onClick={(event) => {
																	event.stopPropagation();
																	setFeedbackByReference((current) => ({
																		...current,
																		[referenceKey]: "irrelevant",
																	}));
																}}
															>
																不相关
															</button>
														</div>
													</div>
												);
											})
										) : (
											<div className="qa-panel-empty">
												发起一轮问答后，这里会列出被检索出来的知识块。
											</div>
										)}
									</div>
								</Card>
							</div>
						)}

						{panelMode === "reference" && activeReference && (
							<div className="qa-panel-stack" data-testid="qa-reference-panel">
								<Card size="small" className="qa-panel-card qa-evidence-card">
									<div className="qa-evidence-card__head">
										<div>
											<Typography.Text strong>
												{activeReference.sourceFilename || activeReference.documentId}
											</Typography.Text>
											<div className="qa-evidence-card__meta">
												<span>{activeReference.documentId}</span>
												<span>分块 #{activeReference.chunkIndex}</span>
												<span>{formatReferenceUpdatedAt(activeReference.sourceUpdatedAt)}</span>
											</div>
										</div>
										<Space wrap>
											<Tag color="blue">
												{formatVersionLabel(activeReference.sourceVersionNumber)}
											</Tag>
											{isStaleReference(activeReference) && (
												<Tag color="warning">
													最新 {formatVersionLabel(activeReference.latestVersionNumber)}
												</Tag>
											)}
										</Space>
									</div>

									<div className="qa-document-preview">
										<div className="qa-document-preview__toolbar">
											<span>Evidence Preview</span>
											<span>askable baseline</span>
										</div>
										<div className="qa-document-preview__page">
											{buildPreviewParagraphs(activeReference).map((paragraph, index) => (
												<p key={`${buildReferenceKey(activeReference)}-${index}`}>
													{index === 1 ? <mark>{paragraph}</mark> : paragraph}
												</p>
											))}
										</div>
									</div>

									<div className="qa-evidence-card__actions">
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
								</Card>

								<Card size="small" className="qa-panel-card">
									<Typography.Text strong>检索片段详情</Typography.Text>
									<div className="qa-inspector-list">
										{currentReferences.map((reference, index) => {
											const selected =
												buildReferenceKey(reference) ===
												buildReferenceKey(activeReference);
											return (
												<div
													key={buildReferenceKey(reference)}
													className={
														selected
															? "qa-inspector-item is-selected"
															: "qa-inspector-item"
													}
													role="button"
													tabIndex={0}
													onClick={() => setActiveReference(reference)}
													onKeyDown={(event) => {
														if (event.key === "Enter" || event.key === " ") {
															event.preventDefault();
															setActiveReference(reference);
														}
													}}
												>
													<div className="qa-inspector-item__head">
														<span className="qa-inspector-item__rank">
															#{index + 1}
														</span>
														<span className="qa-inspector-item__title">
															{reference.sourceFilename || reference.documentId}
														</span>
													</div>
													<div className="qa-inspector-item__preview">
														{reference.contentPreview}
													</div>
												</div>
											);
										})}
									</div>
								</Card>
							</div>
						)}

						{panelMode === "settings" && (
							<div className="qa-panel-stack">
								<Card size="small" className="qa-panel-card">
									<Typography.Text strong>RAG 参数调节</Typography.Text>
									<div className="qa-settings-grid">
										<div className="qa-settings-field">
											<label className="qa-settings-field__label" htmlFor="qa-topk-input">
												Top-K
											</label>
											<InputNumber
												id="qa-topk-input"
												min={1}
												max={20}
												value={topKValue}
												style={{ width: "100%" }}
												onChange={(value) => {
													form.setFieldValue("topK", value ?? 5);
												}}
											/>
										</div>
										<div className="qa-settings-field">
											<div className="qa-settings-field__label">Temperature</div>
											<Input value="暂未开放" disabled />
											<div className="qa-settings-field__hint">
												当前 ask 接口还没有开放 temperature 参数，先保留布局位。
											</div>
										</div>
									</div>
								</Card>

								<Card size="small" className="qa-panel-card">
									<Typography.Text strong>当前说明</Typography.Text>
									<div className="qa-outline-list">
										<div className="qa-outline-list__item">
											Top-K 修改后会直接作用于下一轮问答请求。
										</div>
										<div className="qa-outline-list__item">
											右侧证据面板优先承担“引用核对”和“基线文档入口”职责，不承接 Studio 类型功能。
										</div>
									</div>
								</Card>
							</div>
						)}
					</div>
				</aside>
			</div>
		</div>
	);
}
