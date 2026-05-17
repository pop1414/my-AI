import { z } from "zod";
import { requestJson } from "./request";

const uploadResponseSchema = z.object({
	documentId: z.string().min(1),
	status: z.string().min(1),
});

const documentStatusResponseSchema = z.object({
	documentId: z.string().min(1),
	latestVersionNumber: z.number().int().positive(),
	latestFilename: z.string().min(1),
	latestVersionOriginType: z.string().min(1),
	status: z.string().min(1),
	processingMetadata: z.record(z.string(), z.any()).nullable().optional(),
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

const documentVersionHistoryItemSchema = z.object({
	documentId: z.string().min(1),
	versionNumber: z.number().int().positive(),
	versionOriginType: z.string().min(1),
	rollbackFromVersionNumber: z.number().int().positive().nullable().optional(),
	filename: z.string().min(1),
	fileSize: z.number().int().nonnegative(),
	status: z.string().min(1),
	failureReason: z.string().nullable().optional(),
	createdAt: z.string().min(1),
	updatedAt: z.string().min(1),
	isLatestVersion: z.boolean(),
	isAskableVersion: z.boolean(),
	createdByUserId: z.string().nullable().optional(),
	createdByDisplayName: z.string().nullable().optional(),
	hasBeenRolledBackAsLatest: z.boolean().optional(),
	canRollback: z.boolean().optional(),
});

const documentVersionHistoryResponseSchema = z.object({
	documentId: z.string().min(1),
	sort: z.string().min(1),
	versions: z.array(documentVersionHistoryItemSchema),
});

const documentVersionUploadResponseSchema = z.object({
	documentId: z.string().min(1),
	versionCreated: z.boolean(),
	versionResultType: z.enum(["CREATED", "REUSED_IDENTICAL_CONTENT"]),
	versionNumber: z.number().int().positive().nullable().optional(),
	previousVersionNumber: z.number().int().positive().nullable().optional(),
	reusedLatestVersionNumber: z.number().int().positive().nullable().optional(),
	latestVersionNumber: z.number().int().positive(),
	askableVersionNumber: z.number().int().positive().nullable().optional(),
	canAskNow: z.boolean(),
	status: z.string().min(1),
	versionOriginType: z.string().min(1),
});

const documentVersionRollbackResponseSchema = z.object({
	documentId: z.string().min(1),
	versionNumber: z.number().int().positive(),
	rollbackFromVersionNumber: z.number().int().positive(),
	latestVersionNumber: z.number().int().positive(),
	askableVersionNumber: z.number().int().positive().nullable().optional(),
	canAskNow: z.boolean(),
	status: z.string().min(1),
	versionOriginType: z.string().min(1),
});

export type UploadResponse = z.infer<typeof uploadResponseSchema>;
export type DocumentStatusResponse = z.infer<
	typeof documentStatusResponseSchema
>;
export type DocumentChunksPreviewResponse = z.infer<
	typeof documentChunksPreviewResponseSchema
>;
export type DocumentVersionHistoryItem = z.infer<
	typeof documentVersionHistoryItemSchema
>;
export type DocumentVersionHistoryResponse = z.infer<
	typeof documentVersionHistoryResponseSchema
>;
export type DocumentVersionUploadResponse = z.infer<
	typeof documentVersionUploadResponseSchema
>;
export type DocumentVersionRollbackResponse = z.infer<
	typeof documentVersionRollbackResponseSchema
>;

// ── Document List ────────────────────────────────────────────────

const documentListItemSchema = z.object({
	documentId: z.string().min(1),
	kbId: z.string(),
	latestVersionNumber: z.number().int().positive(),
	latestVersionOriginType: z.string().min(1),
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

export async function uploadNewDocumentVersion(params: {
	documentId: string;
	file: File;
	expectedLatestVersionNumber: number;
}): Promise<DocumentVersionUploadResponse> {
	const formData = new FormData();
	formData.append("file", params.file);
	formData.append(
		"expectedLatestVersionNumber",
		String(params.expectedLatestVersionNumber),
	);

	const response = await requestJson<unknown>(
		`/api/v1/documents/${encodeURIComponent(params.documentId)}/versions`,
		{
			method: "POST",
			body: formData,
		},
	);
	return documentVersionUploadResponseSchema.parse(response);
}

export async function rollbackDocumentVersion(params: {
	documentId: string;
	targetVersionNumber: number;
	expectedLatestVersionNumber: number;
}): Promise<DocumentVersionRollbackResponse> {
	const query = new URLSearchParams({
		expectedLatestVersionNumber: String(params.expectedLatestVersionNumber),
	}).toString();
	const response = await requestJson<unknown>(
		`/api/v1/documents/${encodeURIComponent(params.documentId)}/versions/${params.targetVersionNumber}/rollback?${query}`,
		{
			method: "POST",
		},
	);
	return documentVersionRollbackResponseSchema.parse(response);
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

export async function getDocumentVersionHistory(
	documentId: string,
): Promise<DocumentVersionHistoryResponse> {
	const response = await requestJson<unknown>(
		`/api/v1/documents/${encodeURIComponent(documentId)}/versions`,
	);
	return documentVersionHistoryResponseSchema.parse(response);
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
