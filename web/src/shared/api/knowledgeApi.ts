import { z } from "zod";
import { requestJson } from "./request";

const knowledgeBaseSchema = z.object({
	id: z.string().min(1),
	name: z.string().min(1),
	indexedDocumentCount: z.number().int(),
});

export type KnowledgeBase = z.infer<typeof knowledgeBaseSchema>;

export async function listKnowledgeBases(): Promise<KnowledgeBase[]> {
	const response = await requestJson<unknown>("/api/v1/knowledge-bases");
	return z.array(knowledgeBaseSchema).parse(response);
}
