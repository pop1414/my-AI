import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
	Alert,
	Button,
	Card,
	Empty,
	Form,
	Input,
	InputNumber,
	Select,
	Space,
	Tag,
	Typography,
} from "antd";
import { useSearchParams } from "react-router-dom";
import { z } from "zod";
import {
	askQuestion,
	type AskResponse,
	type AskReference,
} from "../../../shared/api/qaApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import "./QaPage.css";

const qaFormSchema = z.object({
	kbId: z.string().trim().min(1, "请选择知识库"),
	question: z.string().trim().min(1, "请输入问题"),
	topK: z.number().int().min(1, "topK 最小为 1").max(20, "topK 最大为 20"),
});

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
		year: "numeric",
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

function ReferenceCard({ reference }: { reference: AskReference }) {
	const stale = isStaleReference(reference);

	return (
		<Card
			size="small"
			className={stale ? "qa-reference-card is-stale" : "qa-reference-card"}
			data-testid={`reference-card-${reference.documentId}-${reference.chunkIndex}`}
		>
			<div className="qa-reference-card__head">
				<div>
					<Typography.Text className="qa-reference-card__filename">
						{reference.sourceFilename || "未知来源文件"}
					</Typography.Text>
					<Typography.Text
						type="secondary"
						className="qa-reference-card__document"
						copyable={{ text: reference.documentId }}
					>
						{reference.documentId}
					</Typography.Text>
				</div>
				<Tag color={stale ? "warning" : "success"}>
					{formatVersionLabel(reference.sourceVersionNumber)}
				</Tag>
			</div>

			<div className="qa-reference-card__meta">
				<span>来源更新时间：{formatReferenceUpdatedAt(reference.sourceUpdatedAt)}</span>
				<span>分块：#{reference.chunkIndex}</span>
			</div>

			{stale && (
				<div
					className="qa-reference-card__stale"
					data-testid={`reference-stale-${reference.documentId}-${reference.chunkIndex}`}
				>
					当前最新版本为 {formatVersionLabel(reference.latestVersionNumber)}
				</div>
			)}

			<Typography.Paragraph className="qa-reference-card__preview">
				{reference.contentPreview}
			</Typography.Paragraph>
		</Card>
	);
}

export function QaPage() {
	const [searchParams] = useSearchParams();
	const [form] = Form.useForm<{
		kbId: string;
		question: string;
		topK: number;
	}>();
	const [result, setResult] = useState<AskResponse | null>(null);
	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});
	const activeKnowledgeBases = useMemo(
		() => (knowledgeQuery.data ?? []).filter((item) => item.status === "ACTIVE"),
		[knowledgeQuery.data],
	);

	useEffect(() => {
		if (activeKnowledgeBases.length === 0) {
			return;
		}
		const currentKbId = form.getFieldValue("kbId");
		const queryKbId = searchParams.get("kbId");
		const lastKbId = localStorage.getItem("myai:lastKbId");
		const candidate = [currentKbId, queryKbId, lastKbId, "default", activeKnowledgeBases[0]?.id].find(
			(kbId) => (kbId ? activeKnowledgeBases.some((item) => item.id === kbId) : false),
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
		onSuccess: (data) => {
			setResult(data);
			localStorage.setItem("myai:lastKbId", form.getFieldValue("kbId"));
		},
	});

	const onSubmit = async () => {
		const values = qaFormSchema.parse(form.getFieldsValue());
		setResult(null);
		await askMutation.mutateAsync(values);
	};

	const hasReferences = result && result.references.length > 0;
	const staleSummary = result?.staleReferences;
	const shouldShowStaleBanner = Boolean(
		hasReferences && staleSummary?.hasStaleReferences,
	);

	return (
		<Space
			className="qa-page"
			direction="vertical"
			size={16}
			style={{ width: "100%" }}
		>
			<Card
				title="单轮文档问答"
				extra={
					<Typography.Text type="secondary">
						POST /api/v1/qa/ask
					</Typography.Text>
				}
			>
				<Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
					基于知识库文档内容的单轮问答，返回答案与引用分块。
				</Typography.Paragraph>

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
					<Form.Item label="知识库" name="kbId">
						<Select
							placeholder="请选择知识库"
							loading={knowledgeQuery.isLoading}
							options={activeKnowledgeBases.map((item) => ({
								label: `${item.name} (${item.id})`,
								value: item.id,
							}))}
							disabled={knowledgeQuery.isLoading || activeKnowledgeBases.length === 0}
						/>
					</Form.Item>

					<Form.Item label="问题" name="question">
						<Input.TextArea
							placeholder="输入你想问的问题…"
							rows={3}
							maxLength={2000}
							showCount
						/>
					</Form.Item>

					<Form.Item label="topK（检索数量）" name="topK">
						<InputNumber min={1} max={20} style={{ width: 200 }} />
					</Form.Item>

					<Button
						type="primary"
						htmlType="submit"
						loading={askMutation.isPending}
					>
						提交问题
					</Button>
				</Form>
			</Card>

			{knowledgeQuery.isError && <ApiErrorAlert error={knowledgeQuery.error} />}
			{askMutation.isError && <ApiErrorAlert error={askMutation.error} />}

			{result && (
				<>
					{shouldShowStaleBanner && staleSummary && (
						<Alert
							type="warning"
							showIcon
							className="qa-stale-banner"
							data-testid="qa-stale-reference-banner"
							message={`本次回答包含 ${staleSummary.staleReferenceCount} 条非最新版本引用`}
							description={`涉及 ${staleSummary.staleDocumentCount} 个文档。页面下方引用卡片已标出对应 document 的当前最新版本。`}
						/>
					)}

					<Card title="回答">
						<Typography.Paragraph
							style={{
								fontSize: 16,
								lineHeight: 1.8,
								whiteSpace: "pre-wrap",
								margin: 0,
							}}
						>
							{result.answer}
						</Typography.Paragraph>
					</Card>

					<Card
						className="qa-references-card"
						title={
							<Space>
								<span>引用来源</span>
								{hasReferences ? (
									<Tag color="blue">
										{result.references.length} 条
									</Tag>
								) : (
									<Tag color="default">无命中</Tag>
								)}
							</Space>
						}
						extra={
							!hasReferences ? (
								<Typography.Text type="secondary">
									当前问题未命中知识库内容，以上回答为兜底回复。
								</Typography.Text>
							) : null
						}
					>
						{hasReferences ? (
							<div className="qa-reference-list" data-testid="reference-list">
								{result.references.map((reference) => (
									<ReferenceCard
										key={`${reference.documentId}-${reference.chunkIndex}`}
										reference={reference}
									/>
								))}
							</div>
						) : (
							<Empty
								image={Empty.PRESENTED_IMAGE_SIMPLE}
								description="该问题未检索到匹配的文档分块，回答内容来自模型兜底。"
							/>
						)}
					</Card>
				</>
			)}
		</Space>
	);
}
