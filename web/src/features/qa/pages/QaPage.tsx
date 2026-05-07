import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
	Button,
	Card,
	Empty,
	Form,
	Input,
	InputNumber,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useSearchParams } from "react-router-dom";
import { z } from "zod";
import {
	askQuestion,
	type AskResponse,
	type AskReference,
} from "../../../shared/api/qaApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

const qaFormSchema = z.object({
	kbId: z.string().trim().min(1, "请选择知识库"),
	question: z.string().trim().min(1, "请输入问题"),
	topK: z.number().int().min(1, "topK 最小为 1").max(20, "topK 最大为 20"),
});

const referenceColumns: ColumnsType<AskReference> = [
	{
		title: "documentId",
		dataIndex: "documentId",
		width: 320,
		ellipsis: true,
	},
	{ title: "chunkIndex", dataIndex: "chunkIndex", width: 110 },
	{ title: "contentPreview", dataIndex: "contentPreview", ellipsis: true },
];

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

	return (
		<Space direction="vertical" size={16} style={{ width: "100%" }}>
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
							<Table
								rowKey={(row) =>
									`${row.documentId}-${row.chunkIndex}`
								}
								columns={referenceColumns}
								dataSource={result.references}
								pagination={false}
								scroll={{ x: 800 }}
								size="small"
							/>
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
