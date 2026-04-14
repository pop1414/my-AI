import { z } from 'zod';
import { requestJson } from './request';

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
export type DocumentStatusResponse = z.infer<typeof documentStatusResponseSchema>;
export type DocumentChunksPreviewResponse = z.infer<typeof documentChunksPreviewResponseSchema>;

export async function uploadDocument(file: File, kbId?: string): Promise<UploadResponse> {
  const formData = new FormData();
  formData.append('file', file);
  if (kbId && kbId.trim().length > 0) {
    formData.append('kbId', kbId.trim());
  }

  const response = await requestJson<unknown>('/api/v1/documents/upload', {
    method: 'POST',
    body: formData,
  });
  return uploadResponseSchema.parse(response);
}

export async function getDocumentStatus(documentId: string): Promise<DocumentStatusResponse> {
  const response = await requestJson<unknown>(`/api/v1/documents/${encodeURIComponent(documentId)}/status`);
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

export async function reprocessDocument(documentId: string): Promise<DocumentStatusResponse> {
  const response = await requestJson<unknown>(
    `/api/v1/documents/${encodeURIComponent(documentId)}/reprocess`,
    {
      method: 'POST',
    },
  );
  return documentStatusResponseSchema.parse(response);
}

export async function deleteDocument(documentId: string): Promise<void> {
  await requestJson<unknown>(`/api/v1/documents/${encodeURIComponent(documentId)}`, {
    method: 'DELETE',
  });
}
