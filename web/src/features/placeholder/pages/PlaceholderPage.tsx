import { Result } from "antd";
import {
	ConsoleBadgeRow,
	ConsoleMetricCards,
	ConsolePageFrame,
	ConsoleStatePanel,
} from "../../../shared/ui/console/ConsolePageFrame";

export function PlaceholderPage({
	title,
	description,
}: {
	title: string;
	description: string;
}) {
	return (
		<ConsolePageFrame
			eyebrow="Console Placeholder"
			title={title}
			description={description}
			badges={<ConsoleBadgeRow items={[{ label: "共享骨架占位页", color: "default" }]} />}
			summary={
				<ConsoleMetricCards
					items={[
						{
							key: "access",
							label: "当前状态",
							value: "待分配",
							hint: "账号尚未获得可进入的业务模块",
						},
					]}
				/>
			}
			status={
				<ConsoleStatePanel
					tone="warning"
					title="需要管理员介入"
					description="页面骨架仍然保留状态区，用来统一承接访问限制和下一步操作提示。"
				/>
			}
		>
			<Result status="info" title={title} subTitle={description} />
		</ConsolePageFrame>
	);
}
