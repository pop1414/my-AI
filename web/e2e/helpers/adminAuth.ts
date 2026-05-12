import type { APIRequestContext, APIResponse } from "@playwright/test";

type ManagedAccountStatus = "ACTIVE" | "DISABLED";

interface ManagedAccount {
	userId: string;
	username: string;
	userStatus: ManagedAccountStatus;
	failedLoginCount: number;
	lockedUntil?: string | null;
}

interface JsonErrorBody {
	message?: string;
}

const apiBaseUrl = process.env.PLAYWRIGHT_API_BASE_URL ?? "http://localhost:8080";
const adminUsername = process.env.PLAYWRIGHT_ADMIN_USERNAME ?? "admin";
const adminPassword = process.env.PLAYWRIGHT_ADMIN_PASSWORD ?? "admin";

function apiUrl(path: string): string {
	return `${apiBaseUrl}${path}`;
}

async function assertOk(response: APIResponse, context: string): Promise<void> {
	if (response.ok()) {
		return;
	}

	let details = `${response.status()} ${response.statusText()}`;
	try {
		const body = (await response.json()) as JsonErrorBody;
		if (typeof body?.message === "string" && body.message.length > 0) {
			details += ` - ${body.message}`;
		}
	} catch {
		// Ignore non-JSON bodies and preserve the HTTP status details.
	}

	throw new Error(`${context} failed: ${details}`);
}

export async function loginAsAdmin(request: APIRequestContext): Promise<void> {
	const response = await request.post(apiUrl("/api/v1/auth/login"), {
		headers: {
			Accept: "application/json",
			"X-MYAI-CSRF": "1",
		},
		data: {
			username: adminUsername,
			password: adminPassword,
		},
	});
	await assertOk(response, "admin login");
}

export async function getManagedAccountByUsername(
	request: APIRequestContext,
	username: string,
): Promise<ManagedAccount> {
	const response = await request.get(apiUrl("/api/v1/admin/accounts"), {
		headers: {
			Accept: "application/json",
		},
	});
	await assertOk(response, "list managed accounts");

	const accounts = (await response.json()) as ManagedAccount[];
	const account = accounts.find((item) => item.username === username);

	if (!account) {
		throw new Error(`managed account not found for username: ${username}`);
	}

	return account;
}

export async function resetManagedAccountPassword(
	request: APIRequestContext,
	userId: string,
	password: string,
): Promise<void> {
	const response = await request.post(
		apiUrl(`/api/v1/admin/accounts/${encodeURIComponent(userId)}/password/reset`),
		{
			headers: {
				Accept: "application/json",
				"X-MYAI-CSRF": "1",
			},
			data: {
				password,
			},
		},
	);
	await assertOk(response, "reset managed account password");
}

export async function updateManagedAccountStatus(
	request: APIRequestContext,
	userId: string,
	userStatus: ManagedAccountStatus,
): Promise<void> {
	const response = await request.patch(
		apiUrl(`/api/v1/admin/accounts/${encodeURIComponent(userId)}/status`),
		{
			headers: {
				Accept: "application/json",
				"X-MYAI-CSRF": "1",
			},
			data: {
				userStatus,
			},
		},
	);
	await assertOk(response, "update managed account status");
}

export async function prepareManagedAccount(
	request: APIRequestContext,
	options: {
		username: string;
		password: string;
		userStatus: ManagedAccountStatus;
	},
): Promise<ManagedAccount> {
	const account = await getManagedAccountByUsername(request, options.username);

	await resetManagedAccountPassword(request, account.userId, options.password);
	await updateManagedAccountStatus(request, account.userId, options.userStatus);

	return getManagedAccountByUsername(request, options.username);
}
