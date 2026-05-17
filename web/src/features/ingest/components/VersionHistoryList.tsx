import { HistoryOutlined } from "@ant-design/icons";
import { Button, Tag } from "antd";
import { Link } from "react-router-dom";
import { useMemo } from "react";
import { formatTime } from "../utils/formatters";
import type { DocumentVersionHistoryItem } from "../../../shared/api/ingestApi";

const defaultVisibleVersionCount = 8;

interface VersionHistoryListProps {
	historyData: any;
	versions: DocumentVersionHistoryItem[];
	viewingVersion: DocumentVersionHistoryItem;
	latestVersion: DocumentVersionHistoryItem;
	expandedHistory: boolean;
	preservedReturnTo?: string;
	onExpandHistory: () => void;
	buildDetailPath: (id: string, ver?: number, ret?: string) => string;
	getUploader: (v: DocumentVersionHistoryItem) => string;
}

export function VersionHistoryList({
	historyData,
	versions,
	viewingVersion,
	expandedHistory,
	preservedReturnTo,
	onExpandHistory,
	buildDetailPath,
	getUploader,
}: VersionHistoryListProps) {
	const visibleVersions = useMemo(() => {
    if (expandedHistory) return versions;
    return versions.slice(0, defaultVisibleVersionCount);
  }, [expandedHistory, versions]);

	const hiddenVersionCount = versions.length - visibleVersions.length;

	return (
		<aside className="detail-rail">
			<div className="detail-rail__title">
				<HistoryOutlined /> 
				<span>版本账本</span>
				<Tag bordered={false} style={{ marginLeft: 'auto', background: 'var(--detail-canvas-soft)' }}>
          {versions.length} versions
        </Tag>
			</div>

			<div className="detail-history-list">
				{visibleVersions.map((version) => {
					const isActive = version.versionNumber === viewingVersion.versionNumber;
					return (
						<Link
							key={version.versionNumber}
							className={`detail-version-item ${isActive ? "is-active" : ""}`}
							to={buildDetailPath(
								historyData!.documentId,
								version.isLatestVersion ? undefined : version.versionNumber,
								preservedReturnTo,
							)}
						>
							<div className="detail-version-item__top">
								<span className="detail-version-item__num">v{version.versionNumber}</span>
                {version.isLatestVersion && <Tag color="gold" bordered={false} style={{ fontSize: '10px' }}>LATEST</Tag>}
							</div>
							<span className="detail-version-item__file">{version.filename}</span>
							<div className="detail-version-item__meta">
								<span>{getUploader(version)}</span>
								<span>{formatTime(version.updatedAt)}</span>
							</div>
						</Link>
					);
				})}
			</div>

			{hiddenVersionCount > 0 && (
				<Button
					type="text"
					block
					onClick={onExpandHistory}
          style={{ color: 'var(--detail-accent)', fontWeight: 600 }}
				>
					展开更早版本 ({hiddenVersionCount})
				</Button>
			)}
		</aside>
	);
}
