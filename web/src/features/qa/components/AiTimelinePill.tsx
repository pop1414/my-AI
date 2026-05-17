import { Tag } from "antd";

type AiStage = "thinking" | "grep" | "read" | "edit";

interface AiTimelinePillProps {
	stage: AiStage;
	label: string;
}

const stageColorMap: Record<AiStage, string> = {
	thinking: "var(--console-timeline-thinking)",
	grep: "var(--console-timeline-grep)",
	read: "var(--console-timeline-read)",
	edit: "var(--console-timeline-edit)",
};

export function AiTimelinePill({ stage, label }: AiTimelinePillProps) {
	const isDot = !label;
	return (
		<Tag
			bordered={false}
			style={{
				backgroundColor: stageColorMap[stage],
				color: "var(--console-ink)",
				borderRadius: 9999,
				fontSize: 11,
				fontWeight: 600,
				textTransform: "uppercase",
				letterSpacing: "0.08em",
				padding: isDot ? 0 : "2px 10px",
				margin: 0,
				width: isDot ? 10 : "auto",
				height: isDot ? 10 : "auto",
				minWidth: isDot ? 10 : "auto",
				display: "inline-block",
				verticalAlign: "middle",
				border: isDot ? "1px solid rgba(0,0,0,0.05)" : "none"
			}}
		>
			{label}
		</Tag>
	);
}
