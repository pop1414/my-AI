const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
	status: number;
	body: unknown;

	constructor(status: number, message: string, body: unknown) {
		super(message);
		this.name = "ApiError";
		this.status = status;
		this.body = body;
	}
}

/**
 * 控制 401 响应的跳转策略。
 * - `redirect-on-401`：收到 401 且当前不在 /login 页时，自动跳转登录页（默认）
 * - `ignore-401`：由调用方自行处理 401（用于 login/me/logout 等认证接口）
 */
export type AuthPolicy = "redirect-on-401" | "ignore-401";

export interface RequestJsonOptions {
	authPolicy?: AuthPolicy;
}

function isWriteMethod(method?: string): boolean {
	if (!method) return false;
	const m = method.toUpperCase();
	return m !== "GET" && m !== "HEAD" && m !== "OPTIONS";
}

function shouldRedirectOn401(authPolicy: AuthPolicy): boolean {
	if (authPolicy === "ignore-401") return false;
	try {
		return window.location.pathname !== "/login";
	} catch {
		return false;
	}
}

export async function requestJson<T>(
	path: string,
	init?: RequestInit,
	options?: RequestJsonOptions,
): Promise<T> {
	const authPolicy: AuthPolicy = options?.authPolicy ?? "redirect-on-401";
	const isFormData = init?.body instanceof FormData;
	const headers = new Headers(init?.headers);

	if (!isFormData && init?.body && !headers.has("Content-Type")) {
		headers.set("Content-Type", "application/json");
	}
	if (!headers.has("Accept")) {
		headers.set("Accept", "application/json");
	}

	// 写操作统一携带 CSRF Header
	if (isWriteMethod(init?.method) && !headers.has("X-MYAI-CSRF")) {
		headers.set("X-MYAI-CSRF", "1");
	}

	const response = await fetch(`${API_BASE_URL}${path}`, {
		...init,
		credentials: "include",
		headers,
	});

	const text = await response.text();
	let body: unknown = null;

	if (text) {
		try {
			body = JSON.parse(text);
		} catch {
			body = text;
		}
	}

	if (!response.ok) {
		// 401 跳转：仅在 redirect-on-401 策略且不在 /login 页时执行
		if (response.status === 401 && shouldRedirectOn401(authPolicy)) {
			const redirect = window.location.pathname + window.location.search;
			window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`;
			// 返回一个永不 resolve 的 Promise，阻止后续代码继续执行
			return new Promise(() => {});
		}

		const message =
			typeof body === "object" &&
			body !== null &&
			"message" in body &&
			typeof (body as { message?: unknown }).message === "string"
				? (body as { message: string }).message
				: `HTTP ${response.status}`;

		throw new ApiError(response.status, message, body);
	}

	return body as T;
}
