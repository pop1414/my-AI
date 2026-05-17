import { FolderOpenOutlined } from "@ant-design/icons";
import { Empty, Typography } from "antd";
import type { KnowledgeBase } from "../../../shared/api/knowledgeApi";

interface QaSidebarProps {
	loading: boolean;
	knowledgeBases: KnowledgeBase[];
	selectedId: string;
	onSelect: (id: string) => void;
}

export function QaSidebar({
	loading,
	knowledgeBases,
	selectedId,
	onSelect,
}: QaSidebarProps) {
	return (
		<aside className="qa-rag-shell__sources" data-testid="qa-kb-sidebar">
			<div className="qa-rag-shell__section-head">
				<div className="qa-rag-shell__eyebrow">Sources</div>
				<Typography.Title level={4} style={{ margin: 0 }}>
					知识库
				</Typography.Title>
			</div>

			{knowledgeBases.length === 0 && !loading ? (
				<Empty
					image={Empty.PRESENTED_IMAGE_SIMPLE}
					description="暂无激活知识库"
				/>
			) : (
				<div className="qa-source-list">
					{knowledgeBases.map((kb) => {
						const active = kb.id === selectedId;
						return (
							<button
								key={kb.id}
								type="button"
								className={active ? "qa-source-item is-active" : "qa-source-item"}
								onClick={() => onSelect(kb.id)}
								data-testid={`qa-kb-${kb.id}`}
							>
								<div className="qa-source-item__icon">
									<FolderOpenOutlined />
								</div>
								<div className="qa-source-item__copy">
									<div className="qa-source-item__name">{kb.name}</div>
									<div className="qa-source-item__meta">
										{kb.indexedDocumentCount} 篇已索引文档
									</div>
								</div>
							</button>
						);
					})}
				</div>
			)}
		</aside>
	);
}
