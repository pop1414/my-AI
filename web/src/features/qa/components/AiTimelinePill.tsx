import { Tag } from "antd";

type AiStage = "thinking" | "grep" | "read" | "edit";

interface AiTimelinePillProps {
	stage: AiStage;
	label: string;
}

const stageColorMap: Record<AiStage, string> = {
	thinking: "#dfa88f", // Peach
	grep: "#9fc9a2",     // Mint
	read: "#9fbbe0",     // Pastel blue
	edit: "#c0a8dd",     // Lavender
};

export function AiTimelinePill({ stage, label }: AiTimelinePillProps) {
	const isDot = !label;
	return (
		<Tag
			bordered={false}
			style={{
				backgroundColor: stageColorMap[stage],
				color: "#171717",
				borderRadius: 9999,
				fontSize: 11,
				fontWeight: 600,
				textTransform: "uppercase",
				letterSpacing: "0.08em",
				padding: isDot ? 0 : "2px 10px",
				margin: 0,
				width: isDot ? 8 : "auto",
				height: isDot ? 8 : "auto",
				minWidth: isDot ? 8 : "auto",
				display: "inline-block",
				verticalAlign: "middle"
			}}
		>
			{label}
		</Tag>
	);
}
