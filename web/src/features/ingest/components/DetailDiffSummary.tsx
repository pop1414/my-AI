import { Alert, Typography } from "antd";
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
		return (
			<Alert
				type="info"
				showIcon
				message="当前为首个版本，暂无历史差异可比"
			/>
		);
	}

	const fileChanged =
		viewingVersion.filename !== compareVersion.filename ||
		viewingVersion.fileSize !== compareVersion.fileSize;

	return (
		<section className="detail-page__diff" data-testid="diff-summary">
			<div className="detail-page__section-title">
				<Typography.Title level={3}>差异摘要</Typography.Title>
				<Typography.Text type="secondary">
					只比较版本元数据，不做正文 diff。
				</Typography.Text>
			</div>
			<div className="detail-page__diff-grid">
				<div className="detail-page__diff-card ant-card ant-card-bordered ant-card-small">
					<div className="ant-card-body">
						<Typography.Text type="secondary">版本关系</Typography.Text>
						<Typography.Title level={4}>
							v{viewingVersion.versionNumber} vs v
							{compareVersion.versionNumber}
						</Typography.Title>
						<Typography.Paragraph type="secondary">
							{viewingVersion.isLatestVersion
								? "当前最新版本与上一版本对比。"
								: "当前历史版本与系统最新版本对比。"}
						</Typography.Paragraph>
					</div>
				</div>
				<div className="detail-page__diff-card ant-card ant-card-bordered ant-card-small">
					<div className="ant-card-body">
						<Typography.Text type="secondary">文件变化</Typography.Text>
						<Typography.Title level={4}>
							{fileChanged ? "文件事实有变化" : "文件事实一致"}
						</Typography.Title>
						<Typography.Paragraph type="secondary">
							{viewingVersion.filename} · {formatFileSize(viewingVersion.fileSize)}
						</Typography.Paragraph>
					</div>
				</div>
				<div className="detail-page__diff-card ant-card ant-card-bordered ant-card-small">
					<div className="ant-card-body">
						<Typography.Text type="secondary">处理与问答</Typography.Text>
						<Typography.Title level={4}>
							问答基线{" "}
							{askableVersion ? `v${askableVersion.versionNumber}` : "暂无"}
						</Typography.Title>
						<Typography.Paragraph type="secondary">
							当前查看版本状态为 {viewingVersion.status}，对比版本状态为{" "}
							{compareVersion.status}。
						</Typography.Paragraph>
					</div>
				</div>
				<div className="detail-page__diff-card ant-card ant-card-bordered ant-card-small">
					<div className="ant-card-body">
						<Typography.Text type="secondary">时间与来源</Typography.Text>
						<Typography.Title level={4}>
							{originLabel(viewingVersion.versionOriginType)}
						</Typography.Title>
						<Typography.Paragraph type="secondary">
							更新于 {formatTime(viewingVersion.updatedAt)}
						</Typography.Paragraph>
					</div>
				</div>
			</div>
		</section>
	);
}
