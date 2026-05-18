import { expect, test } from "@playwright/test";

const adminUser = {
	userId: "admin-user",
	username: "admin",
	displayName: "治理管理员",
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

const member = {
	userId: "member-user",
	username: "member",
	displayName: "普通成员",
	workspaceId: "workspace-default",
	workspaceRole: "WORKSPACE_MEMBER",
	membershipStatus: "ACTIVE",
};

const documents = Array.from({ length: 6 }, (_, index) => {
	const number = index + 1;
	return {
		documentId: `doc-${number}`,
		kbId: "default",
		latestVersionNumber: 1,
		latestVersionOriginType: "UPLOAD",
		filename: number <= 4 ? `existing-${number}.pdf` : `new-${number}.pdf`,
		fileSize: 1024,
		status: "INDEXED",
		createdAt: "2026-05-18T00:00:00Z",
		updatedAt: "2026-05-18T00:00:00Z",
	};
});

const existingDocumentGrants = documents.slice(0, 4).map((document) => ({
	workspaceId: "workspace-default",
	documentId: document.documentId,
	userId: member.userId,
	username: member.username,
	displayName: member.displayName,
	permission: "DOC_ALLOW_READ",
	status: "ACTIVE",
}));

test.describe("成员授权配置", () => {
	test("文档授权新增计数不以已有授权数量为基数", async ({ page }) => {
		await page.route("**/api/v1/auth/me", async (route) => {
			await route.fulfill({ json: adminUser });
		});
		await page.route("**/api/v1/admin/members", async (route) => {
			await route.fulfill({ json: [member] });
		});
		await page.route("**/api/v1/knowledge-bases", async (route) => {
			await route.fulfill({ json: [] });
		});
		await page.route("**/api/v1/admin/members/member-user/knowledge-base-grants", async (route) => {
			await route.fulfill({ json: [] });
		});
		await page.route("**/api/v1/admin/members/member-user/document-grants", async (route) => {
			await route.fulfill({ json: existingDocumentGrants });
		});
		await page.route("**/api/v1/documents**", async (route) => {
			await route.fulfill({
				json: {
					items: documents,
					total: documents.length,
					limit: 20,
					offset: 0,
				},
			});
		});

		await page.goto("/admin/members/member-user/grants?tab=documents");

		await expect(page.getByTestId("member-document-grant-summary")).toContainText(
			"本次新增 0 项，待移除 0 项",
		);

		await page.locator("tr", { hasText: "new-5.pdf" }).getByRole("checkbox").check();
		await page.locator("tr", { hasText: "new-6.pdf" }).getByRole("checkbox").check();

		await expect(page.getByTestId("member-document-grant-summary")).toContainText(
			"本次新增 2 项，待移除 0 项",
		);
	});
});
