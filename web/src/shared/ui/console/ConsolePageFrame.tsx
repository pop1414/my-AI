import type { ReactNode } from "react";
import {
	CheckCircleFilled,
	ExclamationCircleFilled,
	InboxOutlined,
	LoadingOutlined,
	WarningFilled,
} from "@ant-design/icons";
import { Card, Space, Tag, Typography } from "antd";
import "./ConsolePageFrame.css";

const { Paragraph, Title, Text } = Typography;

export interface ConsoleMetricItem {
	key: string;
	label: string;
	value: ReactNode;
	hint?: ReactNode;
	accent?: "teal" | "slate" | "amber";
}

export interface ConsolePageFrameProps {
	eyebrow: string;
	title: string;
	description: ReactNode;
	badges?: ReactNode;
	actions?: ReactNode;
	summary: ReactNode;
	status: ReactNode;
	children: ReactNode;
}

export type ConsoleStateTone =
	| "loading"
	| "empty"
	| "error"
	| "ready"
	| "warning";

interface ConsoleStatePanelProps {
	tone: ConsoleStateTone;
	title: ReactNode;
	description: ReactNode;
	extra?: ReactNode;
	children?: ReactNode;
	testId?: string;
}

function resolveStateIcon(tone: ConsoleStateTone) {
	switch (tone) {
		case "loading":
			return <LoadingOutlined />;
		case "empty":
			return <InboxOutlined />;
		case "error":
			return <ExclamationCircleFilled />;
		case "warning":
			return <WarningFilled />;
		default:
			return <CheckCircleFilled />;
	}
}

export function ConsolePageFrame({
	eyebrow,
	title,
	description,
	badges,
	actions,
	summary,
	status,
	children,
}: ConsolePageFrameProps) {
	return (
		<section className="console-page-frame" data-testid="console-page-frame">
			<Card className="console-page-frame__header" data-testid="console-page-header">
				<div className="console-page-frame__header-copy">
					<span className="console-page-frame__eyebrow">{eyebrow}</span>
					<div className="console-page-frame__header-main">
						<div>
							<Title level={2} className="console-page-frame__title">
								{title}
							</Title>
							<Paragraph
								type="secondary"
								className="console-page-frame__description"
							>
								{description}
							</Paragraph>
							{badges ? (
								<div className="console-page-frame__badges">{badges}</div>
							) : null}
						</div>
						{actions ? (
							<div className="console-page-frame__actions">{actions}</div>
						) : null}
					</div>
				</div>
			</Card>

			<div className="console-page-frame__summary" data-testid="console-page-summary">
				{summary}
			</div>

			<div className="console-page-frame__body">
				<div
					className="console-page-frame__workspace"
					data-testid="console-page-workspace"
				>
					{children}
				</div>
				<aside
					className="console-page-frame__status"
					data-testid="console-page-status"
				>
					{status}
				</aside>
			</div>
		</section>
	);
}

export function ConsoleMetricCards({
	items,
}: {
	items: ConsoleMetricItem[];
}) {
	return items.map((item) => {
		const accentClass =
			item.accent === "slate"
				? "console-metric-card console-metric-card--slate"
				: item.accent === "amber"
					? "console-metric-card console-metric-card--amber"
					: "console-metric-card";

		return (
			<Card key={item.key} className={accentClass}>
				<Text className="console-metric-card__label">{item.label}</Text>
				<Text className="console-metric-card__value">{item.value}</Text>
				{item.hint ? (
					<Text className="console-metric-card__hint">{item.hint}</Text>
				) : null}
			</Card>
		);
	});
}

export function ConsoleStatePanel({
	tone,
	title,
	description,
	extra,
	children,
	testId,
}: ConsoleStatePanelProps) {
	return (
		<Card
			className={`console-state-panel console-state-panel--${tone}`}
			data-testid={testId}
		>
			<div className="console-state-panel__content">
				<div className="console-state-panel__head">
					<span className="console-state-panel__icon">
						{resolveStateIcon(tone)}
					</span>
					<div className="console-state-panel__copy">
						<Text className="console-state-panel__title">{title}</Text>
						<Text className="console-state-panel__description">
							{description}
						</Text>
					</div>
				</div>
				{extra ? <div>{extra}</div> : null}
				{children}
			</div>
		</Card>
	);
}

export function ConsoleBadgeRow({
	items,
}: {
	items: Array<{ label: string; color?: string }>;
}) {
	return (
		<Space size={[8, 8]} wrap>
			{items.map((item) => (
				<Tag key={`${item.label}-${item.color ?? "default"}`} color={item.color}>
					{item.label}
				</Tag>
			))}
		</Space>
	);
}
