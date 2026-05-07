import { z } from "zod";
import { requestJson } from "./request";

const knowledgeBaseSchema = z.object({
	id: z.string().min(1),
	name: z.string().min(1),
	description: z.string(),
	status: z.enum(["ACTIVE", "INACTIVE"]),
	indexedDocumentCount: z.number().int(),
});

export type KnowledgeBase = z.infer<typeof knowledgeBaseSchema>;

export async function listKnowledgeBases(): Promise<KnowledgeBase[]> {
	const response = await requestJson<unknown>("/api/v1/knowledge-bases");
	return z.array(knowledgeBaseSchema).parse(response);
}

export async function createKnowledgeBase(params: {
	name: string;
	description?: string;
	status?: "ACTIVE" | "INACTIVE";
}): Promise<KnowledgeBase> {
	const response = await requestJson<unknown>("/api/v1/knowledge-bases", {
		method: "POST",
		body: JSON.stringify({
			name: params.name,
			description: params.description ?? "",
			status: params.status ?? "ACTIVE",
		}),
	});
	return knowledgeBaseSchema.parse(response);
}

export async function updateKnowledgeBase(
	kbId: string,
	params: {
		name?: string;
		description?: string;
		status?: "ACTIVE" | "INACTIVE";
	},
): Promise<KnowledgeBase> {
	const response = await requestJson<unknown>(
		`/api/v1/knowledge-bases/${encodeURIComponent(kbId)}`,
		{
			method: "PATCH",
			body: JSON.stringify(params),
		},
	);
	return knowledgeBaseSchema.parse(response);
}
