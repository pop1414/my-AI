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

const uploadAllowedVersionHistory = {
	...versionHistory,
	versions: versionHistory.versions.map((version) =>
		version.versionNumber === 6
			? {
					...version,
					status: "INDEXED",
					isAskableVersion: true,
				}
			: {
					...version,
					isAskableVersion: false,
				},
	),
};

const createdVersionHistory = {
	...versionHistory,
	versions: [
		{
			documentId,
			versionNumber: 7,
			versionOriginType: "UPLOAD",
			rollbackFromVersionNumber: null,
			filename: "policy-2026-v7.docx",
			fileSize: 1_700_000,
			status: "UPLOADED",
			failureReason: null,
			createdAt: "2026-05-14T02:00:00Z",
			updatedAt: "2026-05-14T02:00:00Z",
			isLatestVersion: true,
			isAskableVersion: false,
			createdByUserId: "admin-1",
			createdByDisplayName: "管理员",
		},
		...uploadAllowedVersionHistory.versions.map((version) => ({
			...version,
			isLatestVersion: false,
			isAskableVersion: version.versionNumber === 6,
		})),
	],
};

const rollbackCreatedVersionHistory = {
	...versionHistory,
	versions: [
		{
			documentId,
			versionNumber: 7,
			versionOriginType: "ROLLBACK",
			rollbackFromVersionNumber: 4,
			filename: "policy-2026.docx",
			fileSize: 1_520_000,
			status: "UPLOADED",
			failureReason: null,
			createdAt: "2026-05-14T03:00:00Z",
			updatedAt: "2026-05-14T03:00:00Z",
			isLatestVersion: true,
			isAskableVersion: false,
			createdByUserId: "admin-1",
			createdByDisplayName: "管理员",
		},
		...uploadAllowedVersionHistory.versions.map((version) => ({
			...version,
			isLatestVersion: false,
			isAskableVersion: version.versionNumber === 6,
		})),
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
		await expect(
			page
				.getByLabel("正文阅读接口尚未接入，暂不支持阅读跳转")
				.first(),
		).toBeVisible();
		await expect(page.getByText("展开更早版本（1）")).toBeVisible();

		await page.getByText("展开更早版本（1）").click();

		await expect(page).toHaveURL(/history=expanded/);
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

test.describe("文档详情页上传新版本", () => {
	test("仅当最新版本状态允许时展示上传新版本入口", async ({ page }) => {
		await mockAuthenticatedConsole(page);
		await mockVersionHistory(page);

		await page.goto(`/ingest/documents/${documentId}`);

		await expect(
			page.getByRole("button", { name: "上传新版本" }),
		).toBeHidden();
	});

	test("创建新版本后切换到新的最新版本并展示稳定结果提示", async ({ page }) => {
		await mockAuthenticatedConsole(page);

		let activeHistory: unknown = uploadAllowedVersionHistory;
		let uploadRequestBody = "";
		await page.route(`**/api/v1/documents/${documentId}/versions`, async (route) => {
			if (route.request().method() === "POST") {
				uploadRequestBody = route.request().postData() ?? "";
				activeHistory = createdVersionHistory;
				await route.fulfill({
					json: {
						documentId,
						versionCreated: true,
						versionResultType: "CREATED",
						versionNumber: 7,
						previousVersionNumber: 6,
						reusedLatestVersionNumber: null,
						latestVersionNumber: 7,
						askableVersionNumber: 6,
						canAskNow: true,
						status: "UPLOADED",
						versionOriginType: "UPLOAD",
					},
				});
				return;
			}

			await route.fulfill({ json: activeHistory });
		});

		await page.goto(`/ingest/documents/${documentId}?version=4`);
		await page.getByTestId("return-latest").click();
		await page.getByRole("button", { name: "上传新版本" }).first().click();
		await page
			.locator('input[type="file"]')
			.setInputFiles({
				name: "policy-2026-v7.docx",
				mimeType:
					"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				buffer: Buffer.from("changed version content"),
			});
		await page.getByRole("button", { name: "提交新版本" }).click();

		await expect(page.getByTestId("version-upload-result")).toContainText(
			"已创建新版本 v7",
		);
		await expect(page.getByTestId("version-upload-result")).toContainText(
			"上一版本为 v6",
		);
		await expect(
			page.getByRole("heading", { name: "v7", exact: true }),
		).toBeVisible();
		await expect(page).toHaveURL(new RegExp(`/ingest/documents/${documentId}$`));
		expect(uploadRequestBody).toContain('name="expectedLatestVersionNumber"');
		expect(uploadRequestBody).toContain("6");
		expect(uploadRequestBody).not.toContain('name="kbId"');

		await page.getByRole("button", { name: "查看版本历史" }).click();
		await expect(page).toHaveURL(/history=expanded/);
		await expect(page.getByRole("link", { name: "去问答" })).toBeVisible();
	});

	test("同内容复用时提示未创建新版本并停留在原最新版本", async ({ page }) => {
		await mockAuthenticatedConsole(page);

		await page.route(`**/api/v1/documents/${documentId}/versions`, async (route) => {
			if (route.request().method() === "POST") {
				await route.fulfill({
					json: {
						documentId,
						versionCreated: false,
						versionResultType: "REUSED_IDENTICAL_CONTENT",
						versionNumber: null,
						previousVersionNumber: 6,
						reusedLatestVersionNumber: 6,
						latestVersionNumber: 6,
						askableVersionNumber: 6,
						canAskNow: true,
						status: "INDEXED",
						versionOriginType: "UPLOAD",
					},
				});
				return;
			}

			await route.fulfill({ json: uploadAllowedVersionHistory });
		});

		await page.goto(`/ingest/documents/${documentId}`);
		await page.getByRole("button", { name: "上传新版本" }).first().click();
		await page
			.locator('input[type="file"]')
			.setInputFiles({
				name: "policy-2026-final.docx",
				mimeType:
					"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				buffer: Buffer.from("same version content"),
			});
		await page.getByRole("button", { name: "提交新版本" }).click();

		await expect(page.getByTestId("version-upload-result")).toContainText(
			"未创建新版本",
		);
		await expect(page.getByTestId("version-upload-result")).toContainText(
			"当前仍停留在 v6",
		);
		await expect(
			page.getByRole("heading", { name: "v6", exact: true }),
		).toBeVisible();
		await expect(page.getByTestId("version-card-7")).toBeHidden();
	});
});

test.describe("文档详情页版本回退", () => {
	test("仅对可回退历史版本展示回退入口", async ({ page }) => {
		await mockAuthenticatedConsole(page);
		await page.route(`**/api/v1/documents/${documentId}/versions`, async (route) => {
			await route.fulfill({ json: uploadAllowedVersionHistory });
		});

		await page.goto(`/ingest/documents/${documentId}`);

		await expect(
			page
				.getByTestId("version-card-4")
				.getByRole("button", { name: "回退为最新版本" }),
		).toBeVisible();
		await expect(
			page
				.getByTestId("version-card-6")
				.getByRole("button", { name: "回退为最新版本" }),
		).toBeHidden();
	});

	test("确认回退后展示新最新版本与问答基线提示", async ({ page }) => {
		await mockAuthenticatedConsole(page);

		let activeHistory: unknown = uploadAllowedVersionHistory;
		let rollbackUrl = "";
		await page.route(`**/api/v1/documents/${documentId}/versions**`, async (route) => {
			const request = route.request();
			if (
				request.method() === "POST" &&
				request.url().includes("/versions/4/rollback")
			) {
				rollbackUrl = request.url();
				activeHistory = rollbackCreatedVersionHistory;
				await route.fulfill({
					json: {
						documentId,
						versionNumber: 7,
						rollbackFromVersionNumber: 4,
						latestVersionNumber: 7,
						askableVersionNumber: 6,
						canAskNow: true,
						status: "UPLOADED",
						versionOriginType: "ROLLBACK",
					},
				});
				return;
			}

			await route.fulfill({ json: activeHistory });
		});

		await page.goto(`/ingest/documents/${documentId}`);
		await page
			.getByTestId("version-card-4")
			.getByRole("button", { name: "回退为最新版本" })
			.click();

		await expect(page.getByText("确认回退 v4")).toBeVisible();
		await expect(
			page.getByText("该操作会创建新的最新版本，并可能改变问答基线"),
		).toBeVisible();
		await page.getByRole("button", { name: "确认回退为最新版本" }).click();

		await expect(page.getByTestId("version-rollback-result")).toContainText(
			"已回退为新的最新版本 v7",
		);
		await expect(page.getByTestId("version-rollback-result")).toContainText(
			"历史版本 v4",
		);
		await expect(page.getByTestId("version-rollback-result")).toContainText(
			"新最新版本 v7 尚未 INDEXED",
		);
		await expect(page.getByTestId("version-rollback-result")).toContainText(
			"最近一个已 INDEXED 的版本：v6",
		);
		await expect(
			page.getByRole("heading", { name: "v7", exact: true }),
		).toBeVisible();
		await expect(page.getByTestId("version-card-7")).toContainText(
			"最新版本",
		);
		await expect(page.getByTestId("version-card-7")).toContainText(
			"回退产生",
		);
		await expect(page.getByTestId("version-card-4")).toContainText(
			"曾回退为最新版本",
		);
		expect(rollbackUrl).toContain("expectedLatestVersionNumber=6");
	});
});
