import { Alert, Button, Space, Typography } from "antd";
import { Link, type To } from "react-router-dom";
import type { ReactNode } from "react";
import type { DocumentVersionRollbackResponse } from "../../../shared/api/ingestApi";

interface VersionRollbackResultAlertProps {
	result: DocumentVersionRollbackResponse;
	filename?: string;
	onClose: () => void;
	onShowHistory: () => void;
}

function RouterButtonLink({
	children,
	icon,
	to,
	tone = "default",
	size,
	block,
	testId,
}: {
	children: ReactNode;
	icon?: ReactNode;
	to: To;
	tone?: "default" | "primary" | "text" | "return";
	size?: "small";
	block?: boolean;
	testId?: string;
}) {
	const className = [
		"detail-page__button-link",
		`detail-page__button-link--${tone}`,
		size ? `detail-page__button-link--${size}` : "",
		block ? "detail-page__button-link--block" : "",
	]
		.filter(Boolean)
		.join(" ");

	return (
		<Link className={className} data-testid={testId} to={to}>
			{icon}
			<span>{children}</span>
		</Link>
	);
}

export function VersionRollbackResultAlert({
	result,
	filename,
	onClose,
	onShowHistory,
}: VersionRollbackResultAlertProps) {
	const askableText = result.askableVersionNumber
		? `v${result.askableVersionNumber}`
		: "暂无可问答版本";

	return (
		<Alert
			className="detail-page__result-alert"
			data-result-kind="success"
			data-testid="version-rollback-result"
			aria-live="polite"
			aria-atomic="true"
			type="success"
			showIcon
			message={`已回退为新的最新版本 v${result.latestVersionNumber}`}
			description={
				<div className="detail-page__result-body">
					<Typography.Paragraph>
						已基于历史版本 v{result.rollbackFromVersionNumber} 创建回退版本
						v{result.versionNumber}，页面已切换到新的最新版本。
					</Typography.Paragraph>
					<div className="detail-page__result-facts">
						{filename && (
							<div style={{ marginBottom: 4 }}>
								<Typography.Text strong>文件名：</Typography.Text>
								<span className="ingest-filename">{filename}</span>
							</div>
						)}
						<div><Typography.Text type="secondary">documentId：</Typography.Text>{result.documentId}</div>
						<div><Typography.Text type="secondary">latestVersionNumber：</Typography.Text>v{result.latestVersionNumber}</div>
						<div><Typography.Text type="secondary">rollbackFromVersionNumber：</Typography.Text>v{result.rollbackFromVersionNumber}</div>
						<div><Typography.Text type="secondary">status：</Typography.Text>{result.status}</div>
						<div><Typography.Text type="secondary">askableVersionNumber：</Typography.Text>{askableText}</div>
					</div>
					{result.status !== "INDEXED" && (
						<Alert
							type="warning"
							showIcon
							message={`新最新版本 v${result.latestVersionNumber} 尚未 INDEXED`}
							description={`当前问答暂时仍使用最近一个已 INDEXED 的版本：${askableText}。`}
						/>
					)}
				</div>
			}
			action={
				<Space wrap>
					<Button size="small" onClick={onShowHistory}>
						查看版本历史
					</Button>
					{result.canAskNow && (
						<RouterButtonLink size="small" tone="primary" to="/qa">
							去问答
						</RouterButtonLink>
					)}
					<Button size="small" type="text" onClick={onClose}>
						关闭提示
					</Button>
				</Space>
			}
		/>
	);
}
