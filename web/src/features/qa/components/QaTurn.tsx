import { Avatar, Typography } from "antd";
import { RobotOutlined, UserOutlined } from "@ant-design/icons";
import { AiTimelinePill } from "./AiTimelinePill";
import type { AskReference } from "../../../shared/api/qaApi";

interface QaTurnProps {
	turn: {
		id: string;
		kbName: string;
		question: string;
		answer: string;
		references: AskReference[];
	};
	activeReferenceKey: string;
	onReferenceSelect: (ref: AskReference) => void;
}

export function QaTurn({ turn, activeReferenceKey, onReferenceSelect }: QaTurnProps) {
	return (
		<div className="qa-turn" data-testid="qa-turn">
			{/* User Bubble */}
			<div className="qa-bubble-row qa-bubble-row--user">
				<Avatar
					size={32}
					className="qa-avatar qa-avatar--user"
					icon={<UserOutlined />}
				/>
				<div className="qa-bubble qa-bubble--user">
					<div className="qa-bubble__content">{turn.question}</div>
				</div>
			</div>

			{/* Assistant Bubble */}
			<div className="qa-bubble-row qa-bubble-row--assistant">
				<Avatar
					size={32}
					className="qa-avatar qa-avatar--assistant"
					icon={<RobotOutlined />}
				/>
				<div className="qa-bubble qa-bubble--assistant">
					{/* AI Actions Timeline */}
					<div className="qa-timeline-pills">
						<AiTimelinePill stage="thinking" label="Thinking" />
						<AiTimelinePill stage="grep" label="Searching Vector DB" />
						<AiTimelinePill stage="read" label="Reading Docs" />
						<AiTimelinePill stage="edit" label="Generating" />
					</div>

					<div className="qa-bubble__content qa-bubble__content--answer">
						{turn.answer}
					</div>

					{/* Citations */}
					<div className="qa-citation-strip">
						<div className="qa-citation-strip__title">Citations</div>
						{turn.references.length > 0 ? (
							<div className="qa-citation-strip__list">
								{turn.references.map((ref, index) => {
									const refKey = `${ref.documentId}-${ref.chunkIndex}`;
									const isActive = refKey === activeReferenceKey;
									return (
										<button
											key={refKey}
											type="button"
											className={isActive ? "qa-citation-tag is-active" : "qa-citation-tag"}
											onClick={() => onReferenceSelect(ref)}
										>
											<span className="qa-citation-tag__index">[{index + 1}]</span>
											<span className="qa-citation-tag__label">
												{ref.sourceFilename || "Document"}
											</span>
										</button>
									);
								})}
							</div>
						) : (
							<Typography.Text type="secondary" style={{ fontSize: 13 }}>
								No direct citations found.
							</Typography.Text>
						)}
					</div>
				</div>
			</div>
		</div>
	);
}
