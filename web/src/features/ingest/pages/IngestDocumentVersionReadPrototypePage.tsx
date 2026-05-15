import {
	ArrowLeftOutlined,
	ExpandAltOutlined,
	RetweetOutlined,
	ShrinkOutlined,
} from "@ant-design/icons";
import { Button, Card, Segmented, Select, Space, Tag, Typography } from "antd";
import { useMemo } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
	buildPrototypeDocument,
	formatFileSize,
	formatTime,
	type PrototypeVersion,
	type ResultState,
} from "./IngestDocumentDetailPrototypePage";
import "./IngestDocumentVersionReadPrototypePage.css";

type ReadMode = "single" | "compare";

type ContentSection = {
	title: string;
	paragraphs: string[];
};

function buildContentSections(version: PrototypeVersion): ContentSection[] {
	const policyTierNote =
		version.versionNumber >= 6
			? "本版新增“跨部门联合采购”条款，要求在立项时同步标注预算归口人与复核节点。"
			: version.versionNumber >= 4
				? "本版开始要求在制度中保留预算来源与评审节点，但尚未引入跨部门联合采购口径。"
				: "早期版本对预算来源和复核节点的要求较弱，更适合作为历史审计参考。";

	const supplierNote =
		version.versionOriginType === "ROLLBACK"
			? "该版本由历史版本回退产生，因此正文口径会明显回到更早的制度表达。"
			: version.status === "FAILED"
				? "该版本在治理链路里处于失败语义，但此处阅读页仍只回答“正文内容是什么”。"
				: "该版本由常规上传产生，正文适合直接与其他版本并排对照。";

	return [
		{
			title: "总则",
			paragraphs: [
				`当前正在阅读 v${version.versionNumber}，文件名为 ${version.filename}。这个界面不再承载版本账本和回退说明的主叙事，而是把空间让给正文本身。`,
				"当用户真正想确认某一版制度到底写了什么时，阅读上下文应该像代码 diff 浏览器一样，优先保证长内容的连续阅读与并排比较能力。",
			],
		},
		{
			title: "采购分级",
			paragraphs: [
				"单次采购金额超过五万元时，项目负责人需要附带预算依据、风险说明与供应商选择原则，经审批通过后方可进入正式比选阶段。",
				policyTierNote,
			],
		},
		{
			title: "供应商管理",
			paragraphs: [
				"供应商进入候选池前，应完成资质核验、廉洁承诺与履约记录留痕；若存在例外审批，须在制度正文中保留明确条款。",
				supplierNote,
			],
		},
		{
			title: "归档与留痕",
			paragraphs: [
				"所有采购审批记录、供应商评审意见和合同履约结论应保留在统一归档目录中，形成可审计的正文依据。",
				version.versionNumber >= 5
					? "v5 之后的版本开始强调版本级留痕与治理动作拆分，因此正文里对“归档责任人”描述更明确。"
					: "较早版本尚未把治理动作与正文阅读分离，归档责任人表述相对简化。",
			],
		},
	];
}

function buildBackHref(documentId: string, searchParams: URLSearchParams): string {
	const detailVariant = searchParams.get("detailVariant") ?? "ledger";
	const detailView = searchParams.get("detailView") ?? "7";
	const detailResult = (searchParams.get("detailResult") as ResultState | null) ?? "rollback";

	return `/ingest/documents/${encodeURIComponent(documentId)}/prototype?variant=${detailVariant}&view=${detailView}&result=${detailResult}`;
}

function buildReadHref(params: {
	documentId: string;
	leftVersion: number;
	mode: ReadMode;
	rightVersion?: number;
	sourceParams: URLSearchParams;
}): string {
	const next = new URLSearchParams();
	next.set("mode", params.mode);
	if (params.mode === "compare" && params.rightVersion) {
		next.set("right", String(params.rightVersion));
	}

	const passthroughKeys = ["detailVariant", "detailView", "detailResult"];
	for (const key of passthroughKeys) {
		const value = params.sourceParams.get(key);
		if (value) {
			next.set(key, value);
		}
	}

	return `/ingest/documents/${encodeURIComponent(params.documentId)}/versions/${params.leftVersion}/prototype-read?${next.toString()}`;
}

function ReaderPane({
	side,
	version,
	sections,
	isLatest,
	isAskable,
}: {
	side: "left" | "right";
	version: PrototypeVersion;
	sections: ContentSection[];
	isLatest: boolean;
	isAskable: boolean;
}) {
	return (
		<div className="read-diff-pane">
			<div className="read-diff-pane__header">
				<div className="read-diff-pane__title">
					<Typography.Text className="read-diff-pane__label">
						{side === "left" ? "Left pane" : "Right pane"}
					</Typography.Text>
					<Typography.Title level={4}>
						v{version.versionNumber} · {version.filename}
					</Typography.Title>
				</div>
				<Space wrap>
					{isLatest && <Tag color="gold">最新版本</Tag>}
					{isAskable && <Tag color="green">当前问答基线</Tag>}
					<Tag>{version.versionOriginType}</Tag>
				</Space>
			</div>
			<div className="read-diff-pane__meta">
				<span>{formatFileSize(version.fileSize)}</span>
				<span>{formatTime(version.updatedAt)}</span>
				<span>{version.createdByDisplayName}</span>
				<span>{version.status}</span>
			</div>
			<div className="read-diff-pane__body">
				{sections.map((section) => (
					<section key={`${side}-${version.versionNumber}-${section.title}`}>
						<Typography.Title level={5}>{section.title}</Typography.Title>
						{section.paragraphs.map((paragraph) => (
							<Typography.Paragraph
								key={`${side}-${version.versionNumber}-${section.title}-${paragraph}`}
							>
								{paragraph}
							</Typography.Paragraph>
						))}
					</section>
				))}
			</div>
		</div>
	);
}

export function IngestDocumentVersionReadPrototypePage() {
	const navigate = useNavigate();
	const { documentId = "doc-prototype-001", versionNumber = "4" } = useParams<{
		documentId?: string;
		versionNumber?: string;
	}>();
	const [searchParams] = useSearchParams();

	const mode = (searchParams.get("mode") as ReadMode | null) ?? "single";
	const document = useMemo(() => buildPrototypeDocument(documentId), [documentId]);
	const leftVersionNumber = Number(versionNumber);
	const rightVersionNumber = Number(
		searchParams.get("right") ??
			(leftVersionNumber === document.latestVersionNumber
				? document.askableVersionNumber
				: document.latestVersionNumber),
	);

	const leftVersion =
		document.versions.find((item) => item.versionNumber === leftVersionNumber) ??
		document.versions[0]!;
	const rightVersion =
		document.versions.find((item) => item.versionNumber === rightVersionNumber) ??
		document.versions[1] ??
		document.versions[0]!;

	const leftSections = useMemo(() => buildContentSections(leftVersion), [leftVersion]);
	const rightSections = useMemo(() => buildContentSections(rightVersion), [rightVersion]);
	const backHref = buildBackHref(document.documentId, searchParams);
	const versionOptions = document.versions.map((version) => ({
		label: `v${version.versionNumber} · ${version.filename}`,
		value: version.versionNumber,
	}));

	const goToReadPage = (next: {
		leftVersion?: number;
		mode?: ReadMode;
		rightVersion?: number;
	}) => {
		navigate(
			buildReadHref({
				documentId: document.documentId,
				leftVersion: next.leftVersion ?? leftVersion.versionNumber,
				mode: next.mode ?? mode,
				rightVersion: next.rightVersion ?? rightVersion.versionNumber,
				sourceParams: searchParams,
			}),
		);
	};

	const swapSides = () => {
		goToReadPage({
			leftVersion: rightVersion.versionNumber,
			rightVersion: leftVersion.versionNumber,
			mode: "compare",
		});
	};

	return (
		<div className="read-diff-shell">
			<div className="read-diff-toolbar">
				<div className="read-diff-toolbar__left">
					<Button
						icon={<ArrowLeftOutlined />}
						onClick={() => navigate(backHref)}
					>
						返回版本治理页
					</Button>
					<div className="read-diff-toolbar__title">
						<Typography.Text className="read-diff-toolbar__kicker">
							version reader / compare-ready prototype
						</Typography.Text>
						<Typography.Title level={4}>
							{document.title} · 版本正文阅读
						</Typography.Title>
					</div>
				</div>

				<div className="read-diff-toolbar__right">
					<Segmented
						value={mode}
						onChange={(value) =>
							goToReadPage({ mode: value as ReadMode })
						}
						options={[
							{
								label: (
									<Space size={6}>
										<ShrinkOutlined />
										<span>单栏阅读</span>
									</Space>
								),
								value: "single",
							},
							{
								label: (
									<Space size={6}>
										<ExpandAltOutlined />
										<span>双栏对照</span>
									</Space>
								),
								value: "compare",
							},
						]}
					/>
					<Button onClick={() => goToReadPage({ leftVersion: document.latestVersionNumber })}>
						切到最新版本正文
					</Button>
				</div>
			</div>

			<div className="read-diff-meta">
				<Tag color="gold">最新版本 v{document.latestVersionNumber}</Tag>
				<Tag color="green">问答基线 v{document.askableVersionNumber}</Tag>
				<Tag color="blue">当前左侧 v{leftVersion.versionNumber}</Tag>
				{mode === "compare" && (
					<Tag color="purple">当前右侧 v{rightVersion.versionNumber}</Tag>
				)}
				<Tag>{document.documentId}</Tag>
			</div>

			<Card className="read-diff-control-panel">
				<div className="read-diff-control-panel__grid">
					<div>
						<Typography.Text strong>左侧版本</Typography.Text>
						<Select
							style={{ width: "100%" }}
							value={leftVersion.versionNumber}
							options={versionOptions}
							onChange={(value) =>
								goToReadPage({ leftVersion: Number(value) })
							}
						/>
					</div>
					{mode === "compare" && (
						<div>
							<Typography.Text strong>右侧版本</Typography.Text>
							<Select
								style={{ width: "100%" }}
								value={rightVersion.versionNumber}
								options={versionOptions.filter(
									(option) => option.value !== leftVersion.versionNumber,
								)}
								onChange={(value) =>
									goToReadPage({ rightVersion: Number(value) })
								}
							/>
						</div>
					)}
					<div className="read-diff-control-panel__actions">
						{mode === "compare" && (
							<Button icon={<RetweetOutlined />} onClick={swapSides}>
								交换左右
							</Button>
						)}
						<Button onClick={() => goToReadPage({ mode: "compare" })}>
							选择两个版本并排看
						</Button>
					</div>
				</div>
			</Card>

			<div
				className={`read-diff-canvas ${
					mode === "compare" ? "is-compare" : "is-single"
				}`}
			>
				<ReaderPane
					side="left"
					version={leftVersion}
					sections={leftSections}
					isLatest={leftVersion.versionNumber === document.latestVersionNumber}
					isAskable={leftVersion.versionNumber === document.askableVersionNumber}
				/>
				{mode === "compare" && (
					<ReaderPane
						side="right"
						version={rightVersion}
						sections={rightSections}
						isLatest={rightVersion.versionNumber === document.latestVersionNumber}
						isAskable={rightVersion.versionNumber === document.askableVersionNumber}
					/>
				)}
			</div>
		</div>
	);
}
