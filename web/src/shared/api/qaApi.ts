import { z } from "zod";
import { requestJson } from "./request";

const askReferenceSchema = z.object({
	documentId: z.string(),
	chunkIndex: z.number().int(),
	contentPreview: z.string(),
});

const askResponseSchema = z.object({
	answer: z.string(),
	references: z.array(askReferenceSchema),
});

export type AskResponse = z.infer<typeof askResponseSchema>;
export type AskReference = z.infer<typeof askReferenceSchema>;

export async function askQuestion(params: {
	question: string;
	kbId?: string;
	topK?: number;
}): Promise<AskResponse> {
	const response = await requestJson<unknown>("/api/v1/qa/ask", {
		method: "POST",
		body: JSON.stringify({
			question: params.question,
			kbId: params.kbId || "default",
			topK: params.topK ?? 5,
		}),
	});
	return askResponseSchema.parse(response);
}
