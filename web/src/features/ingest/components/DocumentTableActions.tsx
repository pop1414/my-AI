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
	const showChunksPreview = record.status === "INDEXED";
	const showReprocess = record.status === "FAILED" || record.status === "INDEXED";
	const showDelete = record.status !== "DELETED" && record.status !== "DELETING";

	const encodedId = encodeURIComponent(record.documentId);

	return (
		<div className="console-table-action-group">
			<Tooltip title="查看文档详情、版本与处理上下文">
				<ConsoleLinkButton
					to={`/ingest/documents/${encodedId}?returnTo=${encodeURIComponent(returnTo)}`}
					variant="primary"
					size="small"
					className="console-table-action-link"
				>
					<InfoCircleOutlined /> 查看详情
				</ConsoleLinkButton>
			</Tooltip>
			
			{showChunksPreview && (
				<Tooltip title="查看文档切块结果与预览内容">
					<ConsoleLinkButton
						to={`/ingest/documents/${encodedId}/chunks-preview`}
						size="small"
						className="console-table-action-link"
					>
						<FileSearchOutlined /> 分块预览
					</ConsoleLinkButton>
				</Tooltip>
			)}
			
			{showReprocess && (
				<Tooltip title="将当前文档重新送入处理流水线">
					<ConsoleLinkButton
						to={`/ingest/documents/${encodedId}/reprocess`}
						size="small"
						className="console-table-action-link"
					>
						<FileSyncOutlined /> 重处理
					</ConsoleLinkButton>
				</Tooltip>
			)}
			
			{showDelete && (
				<Tooltip title="删除整个 document 资产及其版本">
					<button
						type="button"
						className="console-link-button console-link-button--small console-action-button--danger"
						onClick={() => onDelete(record)}
						style={{ border: '1px solid #cf2d56' }}
					>
						<DeleteOutlined /> 删除
					</button>
				</Tooltip>
			)}
			
			{canAccessAdmin && (
				<Tooltip title="配置该文档的成员访问权限">
					<ConsoleLinkButton
						to={`/admin/documents/${encodedId}/grants`}
						size="small"
						className="console-table-action-link"
					>
						<SafetyOutlined /> 授权管理
					</ConsoleLinkButton>
				</Tooltip>
			)}
		</div>
	);
}
