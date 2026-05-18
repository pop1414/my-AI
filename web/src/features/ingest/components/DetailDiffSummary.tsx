import { Typography } from "antd";
import { formatFileSize, formatTime, originLabel } from "../utils/formatters";
import type { DocumentVersionHistoryItem } from "../../../shared/api/ingestApi";

interface DetailDiffSummaryProps {
	viewingVersion: DocumentVersionHistoryItem;
	compareVersion?: DocumentVersionHistoryItem;
	askableVersion?: DocumentVersionHistoryItem;
}

export function DetailDiffSummary({
	viewingVersion,
	compareVersion,
	askableVersion,
}: DetailDiffSummaryProps) {
	if (!compareVersion) {
		return null;
	}

	const fileChanged =
		viewingVersion.filename !== compareVersion.filename ||
		viewingVersion.fileSize !== compareVersion.fileSize;

	return (
		<section className="detail-diff-summary" data-testid="diff-summary">
			<div className="detail-diff-card">
				<div className="detail-stat-label">文件事实变化</div>
				<div className="detail-stat-value" style={{ fontSize: '18px', fontWeight: 700 }}>
					{fileChanged ? "内容有差异" : "元数据一致"}
				</div>
				<Typography.Text type="secondary" style={{ fontSize: '12px' }}>
					{viewingVersion.filename} ({formatFileSize(viewingVersion.fileSize)})
				</Typography.Text>
			</div>
			
			<div className="detail-diff-card">
				<div className="detail-stat-label">问答基线状态</div>
				<div className="detail-stat-value" style={{ fontSize: '18px', fontWeight: 700 }}>
					{askableVersion ? `v${askableVersion.versionNumber}` : "未设置"}
				</div>
				<Typography.Text type="secondary" style={{ fontSize: '12px' }}>
					当前版本状态：{viewingVersion.status}
				</Typography.Text>
			</div>

			<div className="detail-diff-card">
				<div className="detail-stat-label">更新溯源</div>
				<div className="detail-stat-value" style={{ fontSize: '18px', fontWeight: 700 }}>
					{originLabel(viewingVersion.versionOriginType)}
				</div>
				<Typography.Text type="secondary" style={{ fontSize: '12px' }}>
					最后同步：{formatTime(viewingVersion.updatedAt)}
				</Typography.Text>
			</div>
		</section>
	);
}
