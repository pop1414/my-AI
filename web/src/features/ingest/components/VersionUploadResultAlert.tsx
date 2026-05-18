import { Alert, Button, Space, Typography } from "antd";
import { Link, type To } from "react-router-dom";
import type { ReactNode } from "react";
import {
        MessageOutlined,
        CheckCircleFilled,
        InfoCircleFilled,
        BookOutlined,
} from "@ant-design/icons";
import type { DocumentVersionUploadResponse } from "../../../shared/api/ingestApi";

interface VersionUploadResultAlertProps {
        result: DocumentVersionUploadResponse;
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

export function VersionUploadResultAlert({
        result,
        filename,
        onClose,
}: VersionUploadResultAlertProps) {
        const visibleVersionNumber =
                result.versionNumber ??
                result.reusedLatestVersionNumber ??
                result.latestVersionNumber;

        const title = result.versionCreated ? "新版本上传成功" : "内容未变更";

        const description = result.versionCreated
                ? `系统已接收新文件并成功创建 v${visibleVersionNumber}。详情页已同步切换。`
                : "检测到上传内容与当前最新版本完全一致，系统未创建冗余版本。";

        return (
                <Alert
                        className={`detail-alert ${result.versionCreated ? "detail-alert--success" : "detail-alert--info"}`}
                        type={result.versionCreated ? "success" : "info"}
                        showIcon
                        icon={
                                result.versionCreated ? (
                                        <CheckCircleFilled
                                                style={{ color: "var(--detail-accent)" }}
                                        />
                                ) : (
                                        <InfoCircleFilled style={{ color: "#1890ff" }} />
                                )
                        }
                        message={
                                <span style={{ fontWeight: 700, fontSize: "15px" }}>
                                        {title}
                                </span>
                        }
                        description={
                                <div
                                        className="detail-page__result-body"
                                        style={{ marginTop: 12 }}
                                >
                                        <Typography.Paragraph
                                                style={{
                                                        marginBottom: 16,
                                                        color: "var(--detail-ink-secondary)",
                                                }}
                                        >
                                                {description}
                                        </Typography.Paragraph>

                                        <div
                                                className="detail-stats-grid"
                                                style={{ marginBottom: 0, gap: "16px" }}
                                        >
                                                {filename && (
                                                        <div className="detail-stat-item">
                                                                <span className="detail-stat-label">
                                                                        关联文件
                                                                </span>
                                                                <span
                                                                        className="detail-stat-value"
                                                                        style={{ fontSize: "13px" }}
                                                                >
                                                                        {filename}
                                                                </span>
                                                        </div>
                                                )}
                                                <div className="detail-stat-item">
                                                        <span className="detail-stat-label">版本状态</span>
                                                        <span
                                                                className="detail-stat-value"
                                                                style={{ fontSize: "13px" }}
                                                        >
                                                                v{visibleVersionNumber} ({result.status})
                                                        </span>
                                                </div>
                                                <div className="detail-stat-item">
                                                        <span className="detail-stat-label">问答基线</span>
                                                        <span
                                                                className="detail-stat-value"
                                                                style={{ fontSize: "13px" }}
                                                        >
                                                                {result.askableVersionNumber
                                                                        ? `v${result.askableVersionNumber}`
                                                                        : "处理中..."}
                                                        </span>
                                                </div>
                                        </div>

                                        {!result.canAskNow && result.versionCreated && (
                                                <div
                                                        style={{
                                                                marginTop: 16,
                                                                padding: "12px",
                                                                background: "#e6f7ff",
                                                                border: "1px solid #91d5ff",
                                                                borderRadius: "6px",
                                                        }}
                                                >
                                                        <Typography.Text
                                                                type="secondary"
                                                                style={{ fontSize: "13px" }}
                                                        >
                                                                正在处理新版本，请耐心等待解析完成后进行问答。
                                                        </Typography.Text>
                                                </div>
                                        )}
                                </div>
                        }
                        action={
                                <Space wrap style={{ marginTop: 16 }}>
                                        {result.versionCreated && result.previousVersionNumber ? (
                                                <RouterButtonLink
                                                        size="small"
                                                        tone="primary"
                                                        to={`/ingest/documents/${encodeURIComponent(result.documentId)}/versions/${visibleVersionNumber}/read?mode=compare&right=${result.previousVersionNumber}`}
                                                        icon={<BookOutlined />}
                                                >
                                                        对比变更
                                                </RouterButtonLink>
                                        ) : (
                                                <RouterButtonLink
                                                        size="small"
                                                        tone="primary"
                                                        to={`/ingest/documents/${encodeURIComponent(result.documentId)}/versions/${visibleVersionNumber}/read`}
                                                        icon={<BookOutlined />}
                                                >
                                                        查看预览
                                                </RouterButtonLink>
                                        )}

                                        {result.canAskNow && (
                                                <RouterButtonLink
                                                        size="small"
                                                        tone="default"
                                                        to="/qa"
                                                        icon={<MessageOutlined />}
                                                >
                                                        去问答
                                                </RouterButtonLink>
                                        )}

                                        <Button
                                                size="small"
                                                type="text"
                                                onClick={onClose}
                                                style={{ color: "var(--detail-ink-faint)" }}
                                        >
                                                关闭
                                        </Button>
                                </Space>
                        }
                />
        );
}