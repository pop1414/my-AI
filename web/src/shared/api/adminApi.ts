import { z } from "zod";
import { requestJson } from "./request";

// ═══════════════════════════════════════════════════════════
// Zod schemas — 严格对齐 docs/04-api-contract.yaml
// ═══════════════════════════════════════════════════════════

// ── 成员 ──

const workspaceMemberSchema = z.object({
	userId: z.string().min(1),
	username: z.string().min(1),
	displayName: z.string(),
	workspaceId: z.string().min(1),
	workspaceRole: z.enum([
		"WORKSPACE_OWNER",
		"WORKSPACE_ADMIN",
		"WORKSPACE_MEMBER",
	]),
	membershipStatus: z.enum(["ACTIVE"]),
});

export type WorkspaceMember = z.infer<typeof workspaceMemberSchema>;

// ── 知识库授权 ──

const knowledgeBaseGrantSchema = z.object({
	workspaceId: z.string().min(1),
	kbId: z.string().min(1),
	userId: z.string().min(1),
	username: z.string().min(1),
	displayName: z.string(),
	role: z.enum(["KB_MANAGER", "KB_CONTRIBUTOR", "KB_READER", "KB_ASKER"]),
	status: z.enum(["ACTIVE"]),
});

export type KnowledgeBaseGrant = z.infer<typeof knowledgeBaseGrantSchema>;

// ── 文档授权 ──

const documentGrantSchema = z.object({
	workspaceId: z.string().min(1),
	documentId: z.string().min(1),
	userId: z.string().min(1),
	username: z.string().min(1),
	displayName: z.string(),
	permission: z.enum(["DOC_ALLOW_READ", "DOC_ALLOW_MANAGE", "DOC_DENY"]),
	status: z.enum(["ACTIVE"]),
});

export type DocumentGrant = z.infer<typeof documentGrantSchema>;

// ── 审计事件 ──

const auditEventSchema = z.object({
	auditEventId: z.number().int(),
	workspaceId: z.string().nullable().optional(),
	actorUserId: z.string().nullable().optional(),
	actorUsername: z.string().nullable().optional(),
	eventType: z.string().min(1),
	targetType: z.string().nullable().optional(),
	targetId: z.string().nullable().optional(),
	outcome: z.enum(["SUCCESS", "FAILURE", "DENIED"]),
	reason: z.string(),
	metadata: z.record(z.string(), z.unknown()).optional(),
	occurredAt: z.string().min(1),
});

export type AuditEvent = z.infer<typeof auditEventSchema>;

const auditEventPageResponseSchema = z.object({
	items: z.array(auditEventSchema),
	total: z.number().int(),
	limit: z.number().int(),
	offset: z.number().int(),
});

export type AuditEventPageResponse = z.infer<
	typeof auditEventPageResponseSchema
>;

// ═══════════════════════════════════════════════════════════
// 成员管理 API
// ═══════════════════════════════════════════════════════════

/** 查询当前工作区有效成员列表。仅 WORKSPACE_OWNER / WORKSPACE_ADMIN 可访问。 */
export async function listMembers(): Promise<WorkspaceMember[]> {
	const response = await requestJson<unknown>("/api/v1/admin/members");
	return z.array(workspaceMemberSchema).parse(response);
}

/** 调整目标成员的工作区角色。 */
export async function updateMemberRole(
	userId: string,
	workspaceRole: WorkspaceMember["workspaceRole"],
): Promise<WorkspaceMember> {
	const response = await requestJson<unknown>(
		`/api/v1/admin/members/${encodeURIComponent(userId)}/role`,
		{
			method: "PATCH",
			body: JSON.stringify({ workspaceRole }),
		},
	);
	return workspaceMemberSchema.parse(response);
}

// ═══════════════════════════════════════════════════════════
// 知识库授权 API
// ═══════════════════════════════════════════════════════════

/** 查询知识库 ACTIVE 授权列表。 */
export async function listKnowledgeBaseGrants(
	kbId: string,
): Promise<KnowledgeBaseGrant[]> {
	const response = await requestJson<unknown>(
		`/api/v1/admin/knowledge-bases/${encodeURIComponent(kbId)}/grants`,
	);
	return z.array(knowledgeBaseGrantSchema).parse(response);
}

/** 授予或更新知识库授权。 */
export async function upsertKnowledgeBaseGrant(
	kbId: string,
	userId: string,
	role: KnowledgeBaseGrant["role"],
): Promise<KnowledgeBaseGrant> {
	const response = await requestJson<unknown>(
		`/api/v1/admin/knowledge-bases/${encodeURIComponent(kbId)}/grants/${encodeURIComponent(userId)}`,
		{
			method: "PUT",
			body: JSON.stringify({ role }),
		},
	);
	return knowledgeBaseGrantSchema.parse(response);
}

/** 回收知识库授权（DISABLED）。 */
export async function deleteKnowledgeBaseGrant(
	kbId: string,
	userId: string,
): Promise<void> {
	await requestJson<unknown>(
		`/api/v1/admin/knowledge-bases/${encodeURIComponent(kbId)}/grants/${encodeURIComponent(userId)}`,
		{ method: "DELETE" },
	);
}

// ═══════════════════════════════════════════════════════════
// 文档授权 API
// ═══════════════════════════════════════════════════════════

/** 查询文档 ACTIVE 授权列表。 */
export async function listDocumentGrants(
	documentId: string,
): Promise<DocumentGrant[]> {
	const response = await requestJson<unknown>(
		`/api/v1/admin/documents/${encodeURIComponent(documentId)}/grants`,
	);
	return z.array(documentGrantSchema).parse(response);
}

/** 授予或更新文档授权。 */
export async function upsertDocumentGrant(
	documentId: string,
	userId: string,
	permission: DocumentGrant["permission"],
): Promise<DocumentGrant> {
	const response = await requestJson<unknown>(
		`/api/v1/admin/documents/${encodeURIComponent(documentId)}/grants/${encodeURIComponent(userId)}`,
		{
			method: "PUT",
			body: JSON.stringify({ permission }),
		},
	);
	return documentGrantSchema.parse(response);
}

/** 回收文档授权（DISABLED）。 */
export async function deleteDocumentGrant(
	documentId: string,
	userId: string,
): Promise<void> {
	await requestJson<unknown>(
		`/api/v1/admin/documents/${encodeURIComponent(documentId)}/grants/${encodeURIComponent(userId)}`,
		{ method: "DELETE" },
	);
}

// ═══════════════════════════════════════════════════════════
// 审计日志 API
// ═══════════════════════════════════════════════════════════

export interface AuditEventListParams {
	eventType?: string;
	actorUserId?: string;
	targetType?: string;
	targetId?: string;
	outcome?: "SUCCESS" | "FAILURE" | "DENIED";
	occurredFrom?: string; // ISO datetime
	occurredTo?: string; // ISO datetime
	limit?: number;
	offset?: number;
}

/** 查询审计事件分页列表。 */
export async function listAuditEvents(
	params?: AuditEventListParams,
): Promise<AuditEventPageResponse> {
	const searchParams = new URLSearchParams();
	if (params) {
		if (params.eventType) searchParams.set("eventType", params.eventType);
		if (params.actorUserId)
			searchParams.set("actorUserId", params.actorUserId);
		if (params.targetType)
			searchParams.set("targetType", params.targetType);
		if (params.targetId) searchParams.set("targetId", params.targetId);
		if (params.outcome) searchParams.set("outcome", params.outcome);
		if (params.occurredFrom)
			searchParams.set("occurredFrom", params.occurredFrom);
		if (params.occurredTo)
			searchParams.set("occurredTo", params.occurredTo);
		if (params.limit !== undefined)
			searchParams.set("limit", String(params.limit));
		if (params.offset !== undefined)
			searchParams.set("offset", String(params.offset));
	}

	const qs = searchParams.toString();
	const path = qs
		? `/api/v1/admin/audit-events?${qs}`
		: "/api/v1/admin/audit-events";

	const response = await requestJson<unknown>(path);
	return auditEventPageResponseSchema.parse(response);
}
