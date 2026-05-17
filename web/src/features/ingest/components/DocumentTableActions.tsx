import { Tooltip } from "antd";
import {
	DeleteOutlined,
	FileSearchOutlined,
	FileSyncOutlined,
	InfoCircleOutlined,
	SafetyOutlined,
} from "@ant-design/icons";
import { ConsoleLinkButton } from "../../../shared/ui/ConsoleLinkButton";
import type { DocumentListItem } from "../../../shared/api/ingestApi";

interface DocumentTableActionsProps {
	record: DocumentListItem;
	canAccessAdmin: boolean;
	returnTo: string;
	onDelete: (record: DocumentListItem) => void;
}

export function DocumentTableActions({
	record,
	canAccessAdmin,
	returnTo,
	onDelete,
}: DocumentTableActionsProps) {
	const isIndexed = record.status === "INDEXED";
	const isFailed = record.status === "FAILED";
	const isDeleted = record.status === "DELETED" || record.status === "DELETING";

	const encodedId = encodeURIComponent(record.documentId);

	return (
		<div className="console-table-action-group">
			<Tooltip title="查看文档详情、版本与处理上下文">
				<ConsoleLinkButton
					to={`/ingest/documents/${encodedId}?returnTo=${encodeURIComponent(returnTo)}`}
					variant="default"
					size="small"
					className="console-table-action-link"
				>
					<InfoCircleOutlined /> 详情
				</ConsoleLinkButton>
			</Tooltip>
			
			<Tooltip title={isIndexed ? "查看文档切块结果与预览内容" : "文档尚未完成索引，无法预览"}>
				<ConsoleLinkButton
					to={`/ingest/documents/${encodedId}/chunks-preview`}
					size="small"
					disabled={!isIndexed}
					className="console-table-action-link"
				>
					<FileSearchOutlined /> 分块预览
				</ConsoleLinkButton>
			</Tooltip>
			
			<Tooltip title={(isFailed || isIndexed) ? "将当前文档重新送入处理流水线" : "当前状态不支持重处理"}>
				<ConsoleLinkButton
					to={`/ingest/documents/${encodedId}/reprocess`}
					size="small"
					disabled={!(isFailed || isIndexed)}
					className="console-table-action-link"
				>
					<FileSyncOutlined /> 重处理
				</ConsoleLinkButton>
			</Tooltip>
			
			{canAccessAdmin && (
				<Tooltip title="配置该文档的成员访问权限">
					<ConsoleLinkButton
						to={`/admin/documents/${encodedId}/grants`}
						size="small"
						className="console-table-action-link"
					>
						<SafetyOutlined /> 授权
					</ConsoleLinkButton>
				</Tooltip>
			)}

			<Tooltip title={isDeleted ? "文档已删除" : "删除整个 document 资产及其版本"}>
				<button
					type="button"
					disabled={isDeleted}
					className={`console-link-button console-link-button--small console-action-button--danger ${isDeleted ? 'console-link-button--disabled' : ''}`}
					onClick={() => !isDeleted && onDelete(record)}
				>
					<DeleteOutlined /> 删除
				</button>
			</Tooltip>
		</div>
	);
}
