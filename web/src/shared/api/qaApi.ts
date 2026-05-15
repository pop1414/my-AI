import { z } from "zod";
import { requestJson } from "./request";

const askReferenceSchema = z.object({
	documentId: z.string(),
	chunkIndex: z.number().int(),
	contentPreview: z.string(),
	sourceVersionNumber: z.number().int().nullable().optional(),
	sourceUpdatedAt: z.string().nullable().optional(),
	isLatestVersion: z.boolean().optional(),
	latestVersionNumber: z.number().int().nullable().optional(),
	sourceFilename: z.string().nullable().optional(),
});

const askStaleReferenceDocumentSchema = z.object({
	documentId: z.string(),
	sourceVersionNumber: z.number().int(),
	latestVersionNumber: z.number().int(),
	sourceFilename: z.string(),
});

const askStaleReferenceSummarySchema = z.object({
	hasStaleReferences: z.boolean(),
	staleReferenceCount: z.number().int(),
	staleDocumentCount: z.number().int(),
	documents: z.array(askStaleReferenceDocumentSchema),
});

const askResponseSchema = z.object({
	answer: z.string(),
	references: z.array(askReferenceSchema),
	staleReferences: askStaleReferenceSummarySchema.nullish(),
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
