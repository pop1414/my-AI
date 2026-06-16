// Three variants of the document detail version history page, switchable via `?variant=`,
// on the throwaway `/ingest/documents/:documentId/prototype` route.
import {
	Alert,
	Button,
	Card,
	Descriptions,
	List,
	Segmented,
	Space,
	Statistic,
	Tag,
	Typography,
} from "antd";
import { useMemo } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { PrototypeVariantSwitcher } from "../../../shared/ui/PrototypeVariantSwitcher";
import { formatTime, formatFileSize, statusColor, buildPrototypeDocument, resultMeta } from "./prototypePageUtils";
import "./IngestDocumentDetailPrototypePage.css";

type VariantKey = "ledger" | "briefing" | "ops";
export type ResultState = "none" | "upload" | "reuse" | "rollback";
export type VersionOriginType = "UPLOAD" | "ROLLBACK";

export type PrototypeVersion = {
	versionNumber: number;
	versionOriginType: VersionOriginType;
	rollbackFromVersionNumber?: number;
	filename: string;
	fileSize: number;
	status: "INDEXED" | "FAILED" | "INGESTING";
	updatedAt: string;
	createdAt: string;
	createdByDisplayName: string;
	isLatestVersion: boolean;
	isAskableVersion: boolean;
	hasBeenRolledBackAsLatest: boolean;
	canRollback: boolean;
	summary: string;
	note: string;
};

export type PrototypeDocument = {
	documentId: string;
	kbId: string;
	title: string;
	latestVersionNumber: number;
	askableVersionNumber: number;
	latestStatus: string;
	latestVersionOriginType: VersionOriginType;
	versions: PrototypeVersion[];
};

export type PrototypeViewModel = {
	document: PrototypeDocument;
	viewingVersion: PrototypeVersion;
	compareVersion: PrototypeVersion | null;
	resultState: ResultState;
	isViewingLatest: boolean;
	canAskNow: boolean;
};

const variantOptions: { key: VariantKey; label: string }[] = [
	{ key: "ledger", label: "账本分栏" },
	{ key: "briefing", label: "简报视图" },
	{ key: "ops", label: "治理控制台" },
];

function HistoryNotice({ model }: { model: PrototypeViewModel }) {
	if (model.isViewingLatest) {
		return null;
	}

	return (
		<Alert
			className="doc-proto-notice"
			type="warning"
			showIcon
			message={`正在查看历史版本 v${model.viewingVersion.versionNumber}`}
			description="此视图仅用于审计与治理查看，不会改变当前最新版本与问答基线。"
		/>
	);
}

function ResultBanner({ model }: { model: PrototypeViewModel }) {
	const meta = resultMeta(model.resultState, model);
	if (!meta) {
		return null;
	}

	return (
		<Alert
			className="doc-proto-banner"
			type={meta.type}
			showIcon
			message={meta.title}
			description={meta.description}
			action={
				<Space direction="vertical" size={8}>
					<Button size="small">查看版本历史</Button>
					{model.canAskNow && <Button size="small" type="primary">去问答</Button>}
				</Space>
			}
		/>
	);
}

function DiffSummary({ model }: { model: PrototypeViewModel }) {
	const compareVersion = model.compareVersion;
	if (!compareVersion) {
		return null;
	}

	const sizeDelta = model.viewingVersion.fileSize - compareVersion.fileSize;
	const compareLabel = model.isViewingLatest
		? `v${model.viewingVersion.versionNumber} vs v${compareVersion.versionNumber}`
		: `v${model.viewingVersion.versionNumber} vs 当前最新 v${compareVersion.versionNumber}`;

	return (
		<div className="doc-proto-diff-grid">
			<Card size="small" className="doc-proto-diff-card">
				<Typography.Text type="secondary">版本关系</Typography.Text>
				<Typography.Title level={5}>{compareLabel}</Typography.Title>
				<Typography.Paragraph type="secondary">
					{model.viewingVersion.versionOriginType === "ROLLBACK"
						? `当前查看版本由 v${model.viewingVersion.rollbackFromVersionNumber} 回退产生。`
						: "当前查看版本来自常规上传链路。"}
				</Typography.Paragraph>
			</Card>
			<Card size="small" className="doc-proto-diff-card">
				<Typography.Text type="secondary">文件变化</Typography.Text>
				<Typography.Title level={5}>
					{model.viewingVersion.filename === compareVersion.filename
						? "文件名未变化"
						: "文件名已变化"}
				</Typography.Title>
				<Typography.Paragraph type="secondary">
					{model.viewingVersion.filename} · {sizeDelta >= 0 ? "+" : ""}
					{Math.round(sizeDelta / 1024)} KB
				</Typography.Paragraph>
			</Card>
			<Card size="small" className="doc-proto-diff-card">
				<Typography.Text type="secondary">处理与问答</Typography.Text>
				<Typography.Title level={5}>
					{model.viewingVersion.status} / askable v
					{model.document.askableVersionNumber}
				</Typography.Title>
				<Typography.Paragraph type="secondary">
					{model.document.latestVersionNumber !== model.document.askableVersionNumber
						? "最新版本尚未可问答，问答入口仍需显式说明版本回退策略。"
						: "最新版本与问答基线一致，可直接承接去问答动作。"}
				</Typography.Paragraph>
			</Card>
			<Card size="small" className="doc-proto-diff-card">
				<Typography.Text type="secondary">时间与来源</Typography.Text>
				<Typography.Title level={5}>
					{formatTime(model.viewingVersion.updatedAt)}
				</Typography.Title>
				<Typography.Paragraph type="secondary">
					来源 {model.viewingVersion.versionOriginType} · 更新人{" "}
					{model.viewingVersion.createdByDisplayName}
				</Typography.Paragraph>
			</Card>
		</div>
	);
}

function PrototypeControls({
	model,
	viewParam,
	onChangeView,
	resultState,
	onChangeResult,
}: {
	model: PrototypeViewModel;
	viewParam: string;
	onChangeView: (next: string) => void;
	resultState: ResultState;
	onChangeResult: (next: ResultState) => void;
}) {
	return (
		<Card className="doc-proto-lab" size="small">
			<Space direction="vertical" size={12} style={{ width: "100%" }}>
				<Alert
					type="info"
					showIcon
					message="PROTOTYPE - 仅用于比较布局与信息层级"
					description="你可以切换 viewing version、结果提示状态和底部 variant，快速观察历史查看态、问答基线提示和稳定结果区在不同布局下的表现。"
				/>
				<div className="doc-proto-lab__row">
					<div>
						<Typography.Text strong>当前查看版本</Typography.Text>
						<Segmented
							block
							value={viewParam}
							onChange={(value) => onChangeView(String(value))}
							options={model.document.versions
								.filter((version) => version.versionNumber >= 2)
								.map((version) => ({
									label:
										version.versionNumber ===
										model.document.latestVersionNumber
											? `最新 v${version.versionNumber}`
											: `历史 v${version.versionNumber}`,
									value: String(version.versionNumber),
								}))}
						/>
					</div>
					<div>
						<Typography.Text strong>结果提示区预览</Typography.Text>
						<Segmented
							block
							value={resultState}
							onChange={(value) => onChangeResult(value as ResultState)}
							options={[
								{ label: "关闭", value: "none" },
								{ label: "上传成功", value: "upload" },
								{ label: "同内容复用", value: "reuse" },
								{ label: "回退成功", value: "rollback" },
							]}
						/>
					</div>
				</div>
				<div className="doc-proto-state">
					<Tag color="blue">viewing v{model.viewingVersion.versionNumber}</Tag>
					<Tag color="gold">latest v{model.document.latestVersionNumber}</Tag>
					<Tag color="green">askable v{model.document.askableVersionNumber}</Tag>
					<Tag>{model.viewingVersion.status}</Tag>
					<Tag>{model.viewingVersion.versionOriginType}</Tag>
				</div>
			</Space>
		</Card>
	);
}

function LedgerHistoryPanel({
	model,
	onSelectVersion,
	onOpenContent,
}: {
	model: PrototypeViewModel;
	onSelectVersion: (versionNumber: number) => void;
	onOpenContent: (versionNumber: number) => void;
}) {
	return (
		<Card className="doc-proto-rail doc-proto-rail--ledger">
			<Typography.Text type="secondary">版本账本</Typography.Text>
			<Typography.Title level={4}>Version ledger</Typography.Title>
			<Space
				direction="vertical"
				size={12}
				style={{ width: "100%" }}
				className="doc-proto-rail__scroll"
			>
				{model.document.versions.map((version) => (
					<button
						key={version.versionNumber}
						type="button"
						className={`doc-proto-version-card ${
							model.viewingVersion.versionNumber === version.versionNumber
								? "is-active"
								: ""
						}`}
						onClick={() => onSelectVersion(version.versionNumber)}
					>
						<div className="doc-proto-version-card__top">
							<span>v{version.versionNumber}</span>
							<Tag color={statusColor(version.status)}>{version.status}</Tag>
						</div>
						<div className="doc-proto-version-card__tags">
							{version.isLatestVersion && <Tag color="gold">最新版本</Tag>}
							{model.viewingVersion.versionNumber === version.versionNumber && (
								<Tag color="blue">当前查看</Tag>
							)}
							{version.isAskableVersion && (
								<Tag color="green">当前问答基线</Tag>
							)}
							{version.versionOriginType === "ROLLBACK" && (
								<Tag color="orange">回退产生</Tag>
							)}
							{version.hasBeenRolledBackAsLatest && (
								<Tag>曾回退为最新版本</Tag>
							)}
						</div>
						<Typography.Text strong>{version.filename}</Typography.Text>
						<Typography.Paragraph type="secondary">
							{formatTime(version.updatedAt)} · {version.createdByDisplayName}
						</Typography.Paragraph>
						<Typography.Paragraph>{version.note}</Typography.Paragraph>
						<div className="doc-proto-inline-actions">
							<Button
								size="small"
								type="link"
								onClick={(event) => {
									event.stopPropagation();
									onOpenContent(version.versionNumber);
								}}
							>
								查看该版本内容
							</Button>
							{version.canRollback && (
								<Button size="small" type="link">
									回退为最新版本
								</Button>
							)}
						</div>
					</button>
				))}
			</Space>
		</Card>
	);
}

function VariantLedger({
	model,
	onSelectVersion,
	onOpenContent,
}: {
	model: PrototypeViewModel;
	onSelectVersion: (versionNumber: number) => void;
	onOpenContent: (versionNumber: number) => void;
}) {
	return (
		<div className="doc-proto-page doc-proto-page--ledger">
			<ResultBanner model={model} />
			<HistoryNotice model={model} />
			<DiffSummary model={model} />
			<div className="doc-proto-hero doc-proto-hero--ledger">
				<div>
					<Typography.Text className="doc-proto-kicker">
						Document detail prototype / ledger split
					</Typography.Text>
					<Typography.Title>
						{model.document.title}
					</Typography.Title>
					<Typography.Paragraph>
						主视图强调"当前看的是谁"，右侧账本强调"版本链如何线性演进"。
					</Typography.Paragraph>
				</div>
				<div className="doc-proto-hero__stats">
					<Statistic title="当前查看版本" value={`v${model.viewingVersion.versionNumber}`} />
					<Statistic title="系统最新版本" value={`v${model.document.latestVersionNumber}`} />
					<Statistic title="问答基线" value={`v${model.document.askableVersionNumber}`} />
				</div>
			</div>
			<div className="doc-proto-grid doc-proto-grid--ledger">
				<div className="doc-proto-main">
					<Card className="doc-proto-panel doc-proto-panel--accent">
						<Typography.Text type="secondary">当前查看版本概览卡</Typography.Text>
						<Typography.Title level={3}>
							v{model.viewingVersion.versionNumber}
						</Typography.Title>
						<Space wrap>
							<Tag color={statusColor(model.viewingVersion.status)}>
								{model.viewingVersion.status}
							</Tag>
							<Tag>{model.viewingVersion.versionOriginType}</Tag>
							{!model.isViewingLatest && (
								<Tag color="orange">
									当前最新为 v{model.document.latestVersionNumber}
								</Tag>
							)}
							{model.document.latestVersionNumber !==
								model.document.askableVersionNumber && (
								<Tag color="gold">
									问答仍使用 v{model.document.askableVersionNumber}
								</Tag>
							)}
						</Space>
						<Typography.Paragraph>
							{model.viewingVersion.summary}
						</Typography.Paragraph>
						<Descriptions column={2} size="small">
							<Descriptions.Item label="documentId">
								{model.document.documentId}
							</Descriptions.Item>
							<Descriptions.Item label="knowledge base">
								{model.document.kbId}
							</Descriptions.Item>
							<Descriptions.Item label="文件名">
								{model.viewingVersion.filename}
							</Descriptions.Item>
							<Descriptions.Item label="文件大小">
								{formatFileSize(model.viewingVersion.fileSize)}
							</Descriptions.Item>
							<Descriptions.Item label="更新时间">
								{formatTime(model.viewingVersion.updatedAt)}
							</Descriptions.Item>
							<Descriptions.Item label="更新人">
								{model.viewingVersion.createdByDisplayName}
							</Descriptions.Item>
						</Descriptions>
						<div className="doc-proto-action-row">
							<Button type="primary">返回最新版本</Button>
							<Button onClick={() => onOpenContent(model.viewingVersion.versionNumber)}>
								查看该版本内容
							</Button>
							{model.viewingVersion.canRollback && (
								<Button>回退为最新版本</Button>
							)}
						</div>
					</Card>
					<Card className="doc-proto-panel">
						<Typography.Title level={5}>文档详情卡</Typography.Title>
						<Typography.Paragraph>
							这里放稳定资产级事实，不跟着历史查看态漂移。适合承接 `documentId`、知识库、治理权限说明。
						</Typography.Paragraph>
					</Card>
					<Card className="doc-proto-panel">
						<Typography.Title level={5}>处理 / 问答上下文卡</Typography.Title>
						<Typography.Paragraph>
							当最新版本尚未 `INDEXED` 时，明确提示问答仍使用 v
							{model.document.askableVersionNumber}，避免把治理主视图和问答基线混在一起。
						</Typography.Paragraph>
					</Card>
				</div>
				<LedgerHistoryPanel
					model={model}
					onSelectVersion={onSelectVersion}
					onOpenContent={onOpenContent}
				/>
			</div>
		</div>
	);
}

function VariantBriefing({
	model,
	onSelectVersion,
	onOpenContent,
}: {
	model: PrototypeViewModel;
	onSelectVersion: (versionNumber: number) => void;
	onOpenContent: (versionNumber: number) => void;
}) {
	return (
		<div className="doc-proto-page doc-proto-page--briefing">
			<ResultBanner model={model} />
			<HistoryNotice model={model} />
			<div className="doc-proto-hero doc-proto-hero--briefing">
				<div>
					<Typography.Text className="doc-proto-kicker">
						Editorial briefing
					</Typography.Text>
					<Typography.Title>
						把版本治理做成一份可读的运营简报
					</Typography.Title>
				</div>
				<div className="doc-proto-briefing__badge">
					<span>v{model.viewingVersion.versionNumber}</span>
					<small>currently viewed</small>
				</div>
			</div>
			<DiffSummary model={model} />
			<div className="doc-proto-grid doc-proto-grid--briefing">
				<Card className="doc-proto-panel doc-proto-panel--stacked">
					<Typography.Title level={5}>版本账本</Typography.Title>
					<List
						dataSource={model.document.versions}
						renderItem={(version) => (
							<List.Item>
								<button
									type="button"
									className={`doc-proto-briefing-item ${
										model.viewingVersion.versionNumber ===
										version.versionNumber
											? "is-active"
											: ""
									}`}
									onClick={() => onSelectVersion(version.versionNumber)}
								>
									<strong>v{version.versionNumber}</strong>
									<span>{version.filename}</span>
									<span>{version.summary}</span>
								</button>
							</List.Item>
						)}
					/>
				</Card>
				<div className="doc-proto-main">
					<Card className="doc-proto-panel doc-proto-panel--editorial">
						<Typography.Text type="secondary">当前查看版本概览卡</Typography.Text>
						<Typography.Title level={2}>
							v{model.viewingVersion.versionNumber}
						</Typography.Title>
						<Typography.Paragraph>
							这一版把"当前查看版本"做成封面式主角，适合强调少量高价值信息。
						</Typography.Paragraph>
						<div className="doc-proto-chip-row">
							<Tag color={statusColor(model.viewingVersion.status)}>
								{model.viewingVersion.status}
							</Tag>
							<Tag color="blue">
								最新 v{model.document.latestVersionNumber}
							</Tag>
							<Tag color="green">
								askable v{model.document.askableVersionNumber}
							</Tag>
						</div>
					</Card>
					<div className="doc-proto-briefing-grid">
						<Card className="doc-proto-panel">
							<Statistic
								title="文件大小"
								value={formatFileSize(model.viewingVersion.fileSize)}
							/>
						</Card>
						<Card className="doc-proto-panel">
							<Statistic
								title="版本来源"
								value={model.viewingVersion.versionOriginType}
							/>
						</Card>
						<Card className="doc-proto-panel">
							<Statistic title="更新人" value={model.viewingVersion.createdByDisplayName} />
						</Card>
					</div>
					<Card className="doc-proto-panel">
						<Typography.Title level={5}>决策说明</Typography.Title>
						<Typography.Paragraph>
							这个版本把"为什么我应该回退 / 为什么我应该返回最新版本"写成更明显的短文块，适合治理动作需要更强解释性的场景。
						</Typography.Paragraph>
						<div className="doc-proto-action-row">
							<Button type="primary">返回最新版本</Button>
							<Button onClick={() => onOpenContent(model.viewingVersion.versionNumber)}>
								查看该版本内容
							</Button>
							{model.viewingVersion.canRollback && (
								<Button>回退为最新版本</Button>
							)}
						</div>
					</Card>
				</div>
			</div>
		</div>
	);
}

function VariantOps({
	model,
	onSelectVersion,
	onOpenContent,
}: {
	model: PrototypeViewModel;
	onSelectVersion: (versionNumber: number) => void;
	onOpenContent: (versionNumber: number) => void;
}) {
	return (
		<div className="doc-proto-page doc-proto-page--ops">
			<ResultBanner model={model} />
			<HistoryNotice model={model} />
			<div className="doc-proto-hero doc-proto-hero--ops">
				<div>
					<Typography.Text className="doc-proto-kicker">
						Governance operations deck
					</Typography.Text>
					<Typography.Title>
						把详情页做成版本治理控制台
					</Typography.Title>
				</div>
				<Space size={12} wrap>
					<Tag color="cyan">latest v{model.document.latestVersionNumber}</Tag>
					<Tag color="lime">askable v{model.document.askableVersionNumber}</Tag>
					<Tag color="magenta">viewing v{model.viewingVersion.versionNumber}</Tag>
				</Space>
			</div>
			<div className="doc-proto-ops-grid">
				<Card className="doc-proto-panel doc-proto-panel--dark">
					<Typography.Title level={5}>状态矩阵</Typography.Title>
					<div className="doc-proto-stat-grid">
						<Statistic title="当前查看" value={`v${model.viewingVersion.versionNumber}`} />
						<Statistic title="最新版本" value={`v${model.document.latestVersionNumber}`} />
						<Statistic title="问答基线" value={`v${model.document.askableVersionNumber}`} />
					</div>
				</Card>
				<Card className="doc-proto-panel doc-proto-panel--dark">
					<Typography.Title level={5}>差异摘要</Typography.Title>
					<DiffSummary model={model} />
				</Card>
				<Card className="doc-proto-panel doc-proto-panel--dark doc-proto-panel--wide">
					<Typography.Title level={5}>版本账本矩阵</Typography.Title>
					<div className="doc-proto-ops-table">
						{model.document.versions.map((version) => (
							<button
								type="button"
								key={version.versionNumber}
								className={`doc-proto-ops-row ${
									model.viewingVersion.versionNumber === version.versionNumber
										? "is-active"
										: ""
								}`}
								onClick={() => onSelectVersion(version.versionNumber)}
							>
								<span>v{version.versionNumber}</span>
								<span>{version.versionOriginType}</span>
								<span>{version.filename}</span>
								<span>{version.createdByDisplayName}</span>
								<span>{formatTime(version.updatedAt)}</span>
								<span>{version.status}</span>
							</button>
						))}
					</div>
					<div className="doc-proto-action-row">
						<Button type="primary">返回最新版本</Button>
						<Button onClick={() => onOpenContent(model.viewingVersion.versionNumber)}>
							查看该版本内容
						</Button>
						{model.viewingVersion.canRollback && (
							<Button>回退为最新版本</Button>
						)}
					</div>
				</Card>
			</div>
		</div>
	);
}

export function IngestDocumentDetailPrototypePage() {
	const navigate = useNavigate();
	const { documentId = "doc-prototype-001" } = useParams<{
		documentId?: string;
	}>();
	const [searchParams, setSearchParams] = useSearchParams();

	const variant = (searchParams.get("variant") as VariantKey | null) ?? "ledger";
	const viewParam = searchParams.get("view") ?? "7";
	const resultState =
		(searchParams.get("result") as ResultState | null) ?? "rollback";

	const document = useMemo(() => buildPrototypeDocument(documentId), [documentId]);
	const viewingVersion =
		document.versions.find(
			(version) => String(version.versionNumber) === viewParam,
		) ?? document.versions[0]!;

	const compareVersion = viewingVersion.isLatestVersion
		? document.versions.find(
				(version) => version.versionNumber === viewingVersion.versionNumber - 1,
			) ?? null
		: document.versions.find((version) => version.isLatestVersion) ?? null;

	const model: PrototypeViewModel = {
		document,
		viewingVersion,
		compareVersion,
		resultState,
		isViewingLatest: viewingVersion.isLatestVersion,
		canAskNow: true,
	};

	const updateSearch = (patch: Record<string, string>) => {
		const next = new URLSearchParams(searchParams);
		for (const [key, value] of Object.entries(patch)) {
			next.set(key, value);
		}
		setSearchParams(next, { replace: true });
	};

	const openContentPrototype = (versionNumber: number) => {
		const readParams = new URLSearchParams({
			mode: "single",
			detailVariant: variant,
			detailView: String(model.viewingVersion.versionNumber),
			detailResult: resultState,
		});
		navigate(
			`/ingest/documents/${encodeURIComponent(document.documentId)}/versions/${versionNumber}/prototype-read?${readParams.toString()}`,
		);
	};

	return (
		<div className="doc-proto-shell">
			<PrototypeControls
				model={model}
				viewParam={viewParam}
				onChangeView={(next) => updateSearch({ view: next })}
				resultState={resultState}
				onChangeResult={(next) => updateSearch({ result: next })}
			/>

			{variant === "ledger" && (
				<VariantLedger
					model={model}
					onSelectVersion={(versionNumber) =>
						updateSearch({ view: String(versionNumber) })
					}
					onOpenContent={openContentPrototype}
				/>
			)}
			{variant === "briefing" && (
				<VariantBriefing
					model={model}
					onSelectVersion={(versionNumber) =>
						updateSearch({ view: String(versionNumber) })
					}
					onOpenContent={openContentPrototype}
				/>
			)}
			{variant === "ops" && (
				<VariantOps
					model={model}
					onSelectVersion={(versionNumber) =>
						updateSearch({ view: String(versionNumber) })
					}
					onOpenContent={openContentPrototype}
				/>
			)}

			<PrototypeVariantSwitcher variants={variantOptions} current={variant} />
		</div>
	);
}
