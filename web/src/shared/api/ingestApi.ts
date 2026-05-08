import { z } from "zod";
import { requestJson } from "./request";

const uploadResponseSchema = z.object({
	documentId: z.string().min(1),
	status: z.string().min(1),
});

const documentStatusResponseSchema = z.object({
	documentId: z.string().min(1),
	status: z.string().min(1),
});

const documentChunkPreviewItemSchema = z.object({
	chunkIndex: z.number().int(),
	contentLength: z.number().int(),
	contentPreview: z.string(),
	truncated: z.boolean(),
	sourceFile: z.string(),
	contentHash: z.string(),
	splitVersion: z.string(),
	sourceHint: z.string().nullable().optional(),
});

const documentChunksPreviewResponseSchema = z.object({
	documentId: z.string().min(1),
	chunkCount: z.number().int(),
	totalChunks: z.number().int(),
	limit: z.number().int(),
	offset: z.number().int(),
	previewChars: z.number().int(),
	chunks: z.array(documentChunkPreviewItemSchema),
});

export type UploadResponse = z.infer<typeof uploadResponseSchema>;
export type DocumentStatusResponse = z.infer<
	typeof documentStatusResponseSchema
>;
export type DocumentChunksPreviewResponse = z.infer<
	typeof documentChunksPreviewResponseSchema
>;

// ── Document List ────────────────────────────────────────────────

const documentListItemSchema = z.object({
	documentId: z.string().min(1),
	kbId: z.string(),
	filename: z.string(),
	fileSize: z.number().int(),
	status: z.string(),
	failureReason: z.string().nullable().optional(),
	createdAt: z.string(),
	updatedAt: z.string(),
});

const documentListPageResponseSchema = z.object({
	items: z.array(documentListItemSchema),
	total: z.number().int(),
	limit: z.number().int(),
	offset: z.number().int(),
});

export type DocumentListItem = z.infer<typeof documentListItemSchema>;
export type DocumentListPageResponse = z.infer<
	typeof documentListPageResponseSchema
>;

export async function listDocuments(params?: {
	kbId?: string;
	status?: string;
	filename?: string;
	limit?: number;
	offset?: number;
}): Promise<DocumentListPageResponse> {
	const query = new URLSearchParams();
	if (params?.kbId) query.set("kbId", params.kbId);
	if (params?.status) query.set("status", params.status);
	if (params?.filename) query.set("filename", params.filename);
	if (params?.limit !== undefined) query.set("limit", String(params.limit));
	if (params?.offset !== undefined)
		query.set("offset", String(params.offset));
	const qs = query.toString();
	const response = await requestJson<unknown>(
		`/api/v1/documents${qs ? `?${qs}` : ""}`,
	);
	return documentListPageResponseSchema.parse(response);
}

export async function uploadDocument(
	file: File,
	kbId?: string,
): Promise<UploadResponse> {
	const formData = new FormData();
	formData.append("file", file);
	if (kbId && kbId.trim().length > 0) {
		formData.append("kbId", kbId.trim());
	}

	const response = await requestJson<unknown>("/api/v1/documents/upload", {
		method: "POST",
		body: formData,
	});
	return uploadResponseSchema.parse(response);
}

export async function getDocumentStatus(
	documentId: string,
): Promise<DocumentStatusResponse> {
	const response = await requestJson<unknown>(
		`/api/v1/documents/${encodeURIComponent(documentId)}/status`,
	);
	return documentStatusResponseSchema.parse(response);
}

export async function getDocumentChunksPreview(params: {
	documentId: string;
	limit: number;
	offset: number;
	previewChars: number;
}): Promise<DocumentChunksPreviewResponse> {
	const query = new URLSearchParams({
		limit: String(params.limit),
		offset: String(params.offset),
		previewChars: String(params.previewChars),
	}).toString();

	const response = await requestJson<unknown>(
		`/api/v1/documents/${encodeURIComponent(params.documentId)}/chunks/preview?${query}`,
	);
	return documentChunksPreviewResponseSchema.parse(response);
}

export async function reprocessDocument(
	documentId: string,
): Promise<DocumentStatusResponse> {
	const response = await requestJson<unknown>(
		`/api/v1/documents/${encodeURIComponent(documentId)}/reprocess`,
		{
			method: "POST",
		},
	);
	return documentStatusResponseSchema.parse(response);
}

export async function deleteDocument(documentId: string): Promise<void> {
	await requestJson<unknown>(
		`/api/v1/documents/${encodeURIComponent(documentId)}`,
		{
			method: "DELETE",
		},
	);
}
