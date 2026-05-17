import { EyeOutlined, HistoryOutlined, RollbackOutlined } from "@ant-design/icons";
import { Button, Space, Tag, Typography } from "antd";
import { Link } from "react-router-dom";
import { useMemo } from "react";
import { formatTime } from "../utils/formatters";
import { VersionTags } from "./VersionTags";
import { RouterButtonLink } from "./RouterButtonLink";
import type { DocumentVersionHistoryItem } from "../../../shared/api/ingestApi";

const defaultVisibleVersionCount = 5;

interface VersionHistoryListProps {
	historyData: any;
	versions: DocumentVersionHistoryItem[];
	viewingVersion: DocumentVersionHistoryItem;
	latestVersion: DocumentVersionHistoryItem;
	expandedHistory: boolean;
	preservedReturnTo?: string;
	onExpandHistory: () => void;
	onRollbackTargetSet: (version: DocumentVersionHistoryItem) => void;
	canRollbackVersion: (v: DocumentVersionHistoryItem, latest: DocumentVersionHistoryItem) => boolean;
	buildDetailPath: (id: string, ver?: number, ret?: string) => string;
	getUploader: (v: DocumentVersionHistoryItem) => string;
	resolveHasBeenRolledBackAsLatest: (v: DocumentVersionHistoryItem, all: DocumentVersionHistoryItem[]) => boolean;
}

function resolveVisibleVersions(
	versions: DocumentVersionHistoryItem[],
	viewingVersion: DocumentVersionHistoryItem,
	expanded: boolean,
): DocumentVersionHistoryItem[] {
	if (expanded || versions.length <= defaultVisibleVersionCount) {
		return versions;
	}

	const mustShow = new Set<number>([
		viewingVersion.versionNumber,
		...versions
			.filter((version) => version.isLatestVersion || version.isAskableVersion)
			.map((version) => version.versionNumber),
	]);
	const defaultVisible = versions
		.slice(0, defaultVisibleVersionCount)
		.map((version) => version.versionNumber);
	const visibleNumbers = new Set([...defaultVisible, ...mustShow]);
	return versions.filter((version) => visibleNumbers.has(version.versionNumber));
}

export function VersionHistoryList({
	historyData,
	versions,
	viewingVersion,
	latestVersion,
	expandedHistory,
	preservedReturnTo,
	onExpandHistory,
	onRollbackTargetSet,
	canRollbackVersion,
	buildDetailPath,
	getUploader,
	resolveHasBeenRolledBackAsLatest,
}: VersionHistoryListProps) {
	const visibleVersions = useMemo(
		() => resolveVisibleVersions(versions, viewingVersion, expandedHistory),
		[expandedHistory, versions, viewingVersion],
	);
	const hiddenVersionCount = versions.length - visibleVersions.length;

	return (
		<div className="detail-page__rail ant-card ant-card-bordered" data-testid="version-history-list">
			<div className="ant-card-body">
				<div className="detail-page__section-title">
					<div>
						<Typography.Text type="secondary">版本历史</Typography.Text>
						<Typography.Title level={3}>
							<HistoryOutlined /> 版本账本
						</Typography.Title>
					</div>
					<Tag>{historyData?.sort}</Tag>
				</div>
				<div className="detail-page__rail-scroll">
					<Space direction="vertical" size={10} style={{ width: "100%" }}>
						{visibleVersions.map((version) => {
							const isActive =
								version.versionNumber === viewingVersion.versionNumber;
							return (
								<div
									key={version.versionNumber}
									className={`detail-page__version-card ${
										isActive ? "is-active" : ""
									}`}
									data-testid={`version-card-${version.versionNumber}`}
								>
									<Link
										className="detail-page__version-card-button"
										to={buildDetailPath(
											historyData!.documentId,
											version.isLatestVersion
												? undefined
												: version.versionNumber,
											preservedReturnTo,
										)}
									>
										<span className="detail-page__version-number">
											v{version.versionNumber}
										</span>
										<VersionTags
											version={version}
											isActive={isActive}
											hasBeenRolledBackAsLatest={resolveHasBeenRolledBackAsLatest(
												version,
												versions,
											)}
										/>
										<span className="detail-page__filename">
											{version.filename}
										</span>
										<span className="detail-page__version-meta">
											{getUploader(version)} · {formatTime(version.updatedAt)}
										</span>
										{version.rollbackFromVersionNumber && (
											<span className="detail-page__version-note">
												回退自 v{version.rollbackFromVersionNumber}
											</span>
										)}
									</Link>
									<div className="detail-page__version-actions">
										<RouterButtonLink
											size="small"
											tone="text"
											icon={<EyeOutlined />}
											to={buildDetailPath(
												historyData!.documentId,
												version.isLatestVersion
													? undefined
													: version.versionNumber,
												preservedReturnTo,
											)}
										>
											查看详情
										</RouterButtonLink>
										{canRollbackVersion(version, latestVersion) && (
											<Button
												size="small"
												type="text"
												icon={<RollbackOutlined />}
												onClick={() => onRollbackTargetSet(version)}
											>
												回退为最新版本
											</Button>
										)}
									</div>
								</div>
							);
						})}
					</Space>
				</div>
				{hiddenVersionCount > 0 && (
					<Button
						block
						onClick={onExpandHistory}
					>
						展开更早版本（{hiddenVersionCount}）
					</Button>
				)}
			</div>
		</div>
	);
}
