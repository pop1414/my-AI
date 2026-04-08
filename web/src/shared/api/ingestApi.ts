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
  contentPreview: z.string(),
  sourceFile: z.string(),
  contentHash: z.string(),
  splitVersion: z.string(),
});

const documentChunksPreviewResponseSchema = z.object({
  documentId: z.string().min(1),
  chunkCount: z.number().int(),
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
  previewChars: number;
}): Promise<DocumentChunksPreviewResponse> {
  const query = new URLSearchParams({
    limit: String(params.limit),
    previewChars: String(params.previewChars),
  }).toString();

  const response = await requestJson<unknown>(
    `/api/v1/documents/${encodeURIComponent(params.documentId)}/chunks/preview?${query}`,
  );
  return documentChunksPreviewResponseSchema.parse(response);
}
