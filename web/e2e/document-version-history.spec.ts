import { expect, test, type Page } from "@playwright/test";

const documentId = "doc-ledger-001";

const currentUser = {
	userId: "admin-1",
	username: "admin",
	displayName: "管理员",
	workspaceId: "workspace-default",
	workspaceRole: "WORKSPACE_ADMIN",
	capabilities: {
		canAccessDocumentList: true,
		canUploadDocument: true,
		canAccessKnowledge: true,
		canAskQuestion: true,
		canAccessAdmin: true,
	},
};

const versionHistory = {
	documentId,
	sort: "versionNumber,DESC",
	versions: [
		{
			documentId,
			versionNumber: 6,
			versionOriginType: "UPLOAD",
			rollbackFromVersionNumber: null,
			filename: "policy-2026-final.docx",
			fileSize: 1_640_000,
			status: "INGESTING",
			failureReason: null,
			createdAt: "2026-05-12T13:21:00Z",
			updatedAt: "2026-05-12T13:35:00Z",
			isLatestVersion: true,
			isAskableVersion: false,
			createdByUserId: "admin-1",
			createdByDisplayName: "管理员",
		},
		{
			documentId,
			versionNumber: 5,
			versionOriginType: "UPLOAD",
			rollbackFromVersionNumber: null,
			filename: "policy-2026-beta.docx",
			fileSize: 1_590_000,
			status: "FAILED",
			failureReason: "parser timeout",
			createdAt: "2026-05-05T02:42:00Z",
			updatedAt: "2026-05-05T03:16:00Z",
			isLatestVersion: false,
			isAskableVersion: false,
			createdByUserId: "admin-1",
			createdByDisplayName: "管理员",
		},
		{
			documentId,
			versionNumber: 4,
			versionOriginType: "UPLOAD",
			rollbackFromVersionNumber: null,
			filename: "policy-2026.docx",
			fileSize: 1_520_000,
			status: "INDEXED",
			failureReason: null,
			createdAt: "2026-04-17T09:10:00Z",
			updatedAt: "2026-04-17T09:44:00Z",
			isLatestVersion: false,
			isAskableVersion: true,
			createdByUserId: "chen-lin",
			createdByDisplayName: "Chen Lin",
		},
		{
			documentId,
			versionNumber: 3,
			versionOriginType: "ROLLBACK",
			rollbackFromVersionNumber: 1,
			filename: "policy-v1.docx",
			fileSize: 1_210_000,
			status: "INDEXED",
			failureReason: null,
			createdAt: "2026-03-15T05:56:00Z",
			updatedAt: "2026-03-15T06:08:00Z",
			isLatestVersion: false,
			isAskableVersion: false,
			createdByUserId: "li-wei",
			createdByDisplayName: "Li Wei",
			hasBeenRolledBackAsLatest: false,
		},
		{
			documentId,
			versionNumber: 2,
			versionOriginType: "UPLOAD",
			rollbackFromVersionNumber: null,
			filename: "policy-v2.docx",
			fileSize: 1_280_000,
			status: "INDEXED",
			failureReason: null,
			createdAt: "2026-02-24T09:50:00Z",
			updatedAt: "2026-02-24T10:19:00Z",
			isLatestVersion: false,
			isAskableVersion: false,
			createdByUserId: "li-wei",
			createdByDisplayName: "Li Wei",
		},
		{
			documentId,
			versionNumber: 1,
			versionOriginType: "UPLOAD",
			rollbackFromVersionNumber: null,
			filename: "policy-v1.docx",
			fileSize: 1_210_000,
			status: "INDEXED",
			failureReason: null,
			createdAt: "2026-02-02T00:42:00Z",
			updatedAt: "2026-02-02T01:14:00Z",
			isLatestVersion: false,
			isAskableVersion: false,
			createdByUserId: "li-wei",
			createdByDisplayName: "Li Wei",
			hasBeenRolledBackAsLatest: true,
		},
	],
};

async function mockAuthenticatedConsole(page: Page) {
	await page.route("**/api/v1/auth/me", async (route) => {
		await route.fulfill({ json: currentUser });
	});
}

async function mockVersionHistory(page: Page) {
	await page.route(`**/api/v1/documents/${documentId}/versions`, async (route) => {
		await route.fulfill({ json: versionHistory });
	});
}

test.describe("文档详情页版本历史只读视图", () => {
	test("展示版本历史列表并保留问答基线版本可见", async ({ page }) => {
		await mockAuthenticatedConsole(page);
		await mockVersionHistory(page);

		await page.goto(`/ingest/documents/${documentId}`);

		await expect(page.getByRole("heading", { name: "文档详情" })).toBeVisible();
		await expect(page.getByTestId("version-history-list")).toContainText(
			"版本账本",
		);
		await expect(page.getByTestId("version-card-6")).toContainText(
			"最新版本",
		);
		await expect(page.getByTestId("version-card-4")).toContainText(
			"当前问答基线",
		);
		await expect(page.getByTestId("version-card-4")).toContainText("Chen Lin");
		await expect(page.getByText("展开更早版本（1）")).toBeVisible();

		await page.getByText("展开更早版本（1）").click();

		await expect(page.getByTestId("version-card-1")).toContainText(
			"曾回退为最新版本",
		);
	});

	test("查看历史版本时提示不会改变最新版本与问答基线", async ({ page }) => {
		await mockAuthenticatedConsole(page);
		await mockVersionHistory(page);

		await page.goto(`/ingest/documents/${documentId}?version=4`);

		await expect(page.getByTestId("history-alert")).toContainText(
			"正在查看历史版本 v4",
		);
		await expect(page.getByTestId("diff-summary")).toContainText("v4 vs v6");
		await expect(page.getByText("问答基线 v4")).toBeVisible();

		await page.getByTestId("return-latest").click();

		await expect(page).toHaveURL(new RegExp(`/ingest/documents/${documentId}$`));
		await expect(page.getByTestId("history-alert")).toBeHidden();
	});

	test("无管理权限时不展示旧版本历史视图", async ({ page }) => {
		await mockAuthenticatedConsole(page);
		await page.route(`**/api/v1/documents/${documentId}/versions`, async (route) => {
			await route.fulfill({
				status: 403,
				json: { message: "Forbidden" },
			});
		});

		await page.goto(`/ingest/documents/${documentId}`);

		await expect(page.getByText("旧版本视图不可见")).toBeVisible();
		await expect(page.getByTestId("version-history-list")).toBeHidden();
	});
});
