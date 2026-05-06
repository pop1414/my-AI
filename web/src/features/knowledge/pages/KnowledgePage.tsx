import { useQuery } from "@tanstack/react-query";
import { Button, Card, Empty, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useNavigate } from "react-router-dom";
import {
	listKnowledgeBases,
	type KnowledgeBase,
} from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

const columns: ColumnsType<KnowledgeBase> = [
	{ title: "知识库 ID", dataIndex: "id", width: 320 },
	{ title: "名称", dataIndex: "name", width: 200 },
	{ title: "已索引文档数", dataIndex: "indexedDocumentCount", width: 140 },
	{
		title: "操作",
		key: "action",
		width: 120,
		render: (_, record) => (
			<Button
				type="primary"
				size="small"
				onClick={() => {
					localStorage.setItem("myai:lastKbId", record.id);
					window.open(
						`/qa?kbId=${encodeURIComponent(record.id)}`,
						"_blank",
					);
				}}
			>
				去问答
			</Button>
		),
	},
];

export function KnowledgePage() {
	const navigate = useNavigate();

	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});

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
					展示所有知识库及其已索引文档数量，可快速跳转至问答页。
				</Typography.Paragraph>
			</Card>

			{knowledgeQuery.isError && (
				<ApiErrorAlert error={knowledgeQuery.error} />
			)}

			<Card>
				{knowledgeQuery.isLoading ? (
					<Table
						loading
						columns={columns}
						dataSource={[]}
						rowKey="id"
					/>
				) : knowledgeQuery.data && knowledgeQuery.data.length === 0 ? (
					<Empty description="暂无知识库，请先上传文档并等待入库完成。" />
				) : (
					<Table
						rowKey="id"
						columns={columns}
						dataSource={knowledgeQuery.data}
						loading={knowledgeQuery.isFetching}
						onRow={(record) => ({
							style: { cursor: "pointer" },
							onClick: () => {
								localStorage.setItem(
									"myai:lastKbId",
									record.id,
								);
								navigate(
									`/qa?kbId=${encodeURIComponent(record.id)}`,
								);
							},
						})}
						pagination={false}
					/>
				)}
			</Card>
		</Space>
	);
}
