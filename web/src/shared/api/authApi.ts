import { z } from "zod";
import { requestJson } from "./request";

// ── Zod schemas（严格对齐 docs/04-api-contract.yaml） ──

const currentUserResponseSchema = z.object({
	userId: z.string().min(1),
	username: z.string().min(1),
	displayName: z.string(),
	workspaceId: z.string().min(1),
	workspaceRole: z.enum([
		"WORKSPACE_OWNER",
		"WORKSPACE_ADMIN",
		"WORKSPACE_MEMBER",
	]),
	capabilities: z.object({
		canAccessDocumentList: z.boolean(),
		canUploadDocument: z.boolean(),
		canAccessKnowledge: z.boolean(),
		canAskQuestion: z.boolean(),
		canAccessAdmin: z.boolean(),
	}),
});

export type CurrentUserResponse = z.infer<typeof currentUserResponseSchema>;

const loginResponseSchema = z.object({
	user: currentUserResponseSchema,
});

export type LoginResponse = z.infer<typeof loginResponseSchema>;

// ── API 函数 ──

/**
 * 本地账号登录。
 * 使用 `ignore-401` 策略，避免登录失败时被请求层全局 401 跳转拦截。
 */
export async function login(
	username: string,
	password: string,
): Promise<LoginResponse> {
	const response = await requestJson<unknown>(
		"/api/v1/auth/login",
		{
			method: "POST",
			body: JSON.stringify({ username, password }),
		},
		{ authPolicy: "ignore-401" },
	);
	return loginResponseSchema.parse(response);
}

/**
 * 登出当前 Session。
 * 使用 `ignore-401` 策略：后端返回 204（成功）或 401（Session 已失效）均视为登出完成。
 */
export async function logout(): Promise<void> {
	await requestJson<unknown>(
		"/api/v1/auth/logout",
		{ method: "POST" },
		{ authPolicy: "ignore-401" },
	);
}

/**
 * 查询当前登录用户。
 * 使用 `ignore-401` 策略：未登录时由调用方（AuthProvider）自行处理匿名态。
 */
export async function getCurrentUser(): Promise<CurrentUserResponse> {
	const response = await requestJson<unknown>("/api/v1/auth/me", undefined, {
		authPolicy: "ignore-401",
	});
	return currentUserResponseSchema.parse(response);
}
