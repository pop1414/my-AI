import { Alert, Button, Space, Typography } from "antd";
import { Link, type To } from "react-router-dom";
import type { ReactNode } from "react";
import { MessageOutlined, CheckCircleOutlined } from "@ant-design/icons";
import type { DocumentVersionRollbackResponse } from "../../../shared/api/ingestApi";

interface VersionRollbackResultAlertProps {
	result: DocumentVersionRollbackResponse;
	filename?: string;
	onClose: () => void;
}

function RouterButtonLink({
	children,
	icon,
	to,
	tone = "default",
	size,
}: {
	children: ReactNode;
	icon?: ReactNode;
	to: To;
	tone?: "default" | "primary" | "text" | "return";
	size?: "small";
}) {
	const className = [
		"detail-page__button-link",
		`detail-page__button-link--${tone}`,
		size ? `detail-page__button-link--${size}` : "",
	]
		.filter(Boolean)
		.join(" ");

	return (
		<Link className={className} to={to}>
			{icon}
			<span>{children}</span>
		</Link>
	);
}

export function VersionRollbackResultAlert({
	result,
	filename,
	onClose,
}: VersionRollbackResultAlertProps) {
	const askableText = result.askableVersionNumber
		? `v${result.askableVersionNumber}`
		: "暂无可问答版本";

	return (
		<Alert
			className="detail-alert detail-alert--success"
			type="success"
			showIcon
      icon={<CheckCircleOutlined style={{ color: 'var(--detail-accent)' }} />}
			message={<span style={{ fontWeight: 700, fontSize: '15px' }}>版本回退成功</span>}
			description={
				<div className="detail-page__result-body" style={{ marginTop: 12 }}>
					<Typography.Paragraph style={{ marginBottom: 16, color: 'var(--detail-ink-secondary)' }}>
						已基于历史版本 v{result.rollbackFromVersionNumber} 创建回退版本
						<Typography.Text strong> v{result.versionNumber} </Typography.Text>，页面已自动切换至该版本。
					</Typography.Paragraph>
					
          <div className="detail-stats-grid" style={{ marginBottom: 0, gap: '16px' }}>
						{filename && (
							<div className="detail-stat-item">
								<span className="detail-stat-label">关联文件</span>
								<span className="detail-stat-value" style={{ fontSize: '13px' }}>{filename}</span>
							</div>
						)}
						<div className="detail-stat-item">
							<span className="detail-stat-label">新版本号</span>
							<span className="detail-stat-value" style={{ fontSize: '13px' }}>v{result.versionNumber}</span>
						</div>
						<div className="detail-stat-item">
							<span className="detail-stat-label">问答基线</span>
							<span className="detail-stat-value" style={{ fontSize: '13px' }}>{askableText}</span>
						</div>
					</div>

					{result.status !== "INDEXED" && (
						<div style={{ marginTop: 16, padding: '12px', background: '#fffbe6', border: '1px solid #ffe58f', borderRadius: '6px' }}>
							<Typography.Text type="warning" style={{ fontSize: '13px' }}>
								注意：新生成的 v{result.versionNumber} 尚未完成索引，问答系统暂时仍锁定在 {askableText}。
							</Typography.Text>
						</div>
					)}
				</div>
			}
			action={
				<Space wrap style={{ marginTop: 8 }}>
					{result.canAskNow && (
						<RouterButtonLink size="small" tone="primary" to="/qa" icon={<MessageOutlined />}>
							立即问答
						</RouterButtonLink>
					)}
					<Button size="small" type="text" onClick={onClose} style={{ color: 'var(--detail-ink-faint)' }}>
						关闭
					</Button>
				</Space>
			}
		/>
	);
}
