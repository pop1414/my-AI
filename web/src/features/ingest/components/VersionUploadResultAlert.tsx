import { Alert, Button, Space, Typography } from "antd";
import { Link, type To } from "react-router-dom";
import type { ReactNode } from "react";
import type { DocumentVersionUploadResponse } from "../../../shared/api/ingestApi";

interface VersionUploadResultAlertProps {
	result: DocumentVersionUploadResponse;
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

export function VersionUploadResultAlert({
	result,
	onClose,
	onShowHistory,
}: VersionUploadResultAlertProps) {
	const visibleVersionNumber =
		result.versionNumber ??
		result.reusedLatestVersionNumber ??
		result.latestVersionNumber;
	const title = result.versionCreated
		? `已创建新版本 v${visibleVersionNumber}`
		: "未创建新版本";
	const description = result.versionCreated
		? `上一版本为 v${result.previousVersionNumber}，当前详情页已切换到最新版本。`
		: `上传文件与当前最新版本内容一致，当前仍停留在 v${visibleVersionNumber}。`;

	return (
		<Alert
			className="detail-page__result-alert"
			data-result-kind={result.versionCreated ? "success" : "info"}
			data-testid="version-upload-result"
			aria-live="polite"
			aria-atomic="true"
			type={result.versionCreated ? "success" : "info"}
			showIcon
			message={title}
			description={
				<div className="detail-page__result-body">
					<Typography.Paragraph>{description}</Typography.Paragraph>
					<div className="detail-page__result-facts">
						<span>documentId：{result.documentId}</span>
						<span>latestVersionNumber：v{result.latestVersionNumber}</span>
						<span>
							previousVersionNumber：
							{result.previousVersionNumber
								? `v${result.previousVersionNumber}`
								: "-"}
						</span>
						<span>status：{result.status}</span>
						<span>
							askableVersionNumber：
							{result.askableVersionNumber
								? `v${result.askableVersionNumber}`
								: "暂无"}
						</span>
					</div>
					{!result.canAskNow && (
						<Typography.Text type="secondary">
							当前暂无可问答版本，请等待处理完成。
						</Typography.Text>
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
