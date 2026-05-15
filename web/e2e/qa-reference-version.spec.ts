import { expect, test, type Page } from "@playwright/test";

const currentUser = {
	userId: "user-qa",
	username: "qa_user",
	displayName: "问答用户",
	workspaceId: "workspace-default",
	workspaceRole: "WORKSPACE_MEMBER",
	capabilities: {
		canAccessDocumentList: true,
		canUploadDocument: false,
		canAccessKnowledge: true,
		canAskQuestion: true,
		canAccessAdmin: false,
	},
};

const knowledgeBases = [
	{
		id: "default",
		name: "默认知识库",
		description: "默认问答知识库",
		status: "ACTIVE",
		indexedDocumentCount: 2,
	},
];

async function mockQaShell(page: Page) {
	await page.route("**/api/v1/auth/me", async (route) => {
		await route.fulfill({ json: currentUser });
	});
	await page.route("**/api/v1/knowledge-bases", async (route) => {
		await route.fulfill({ json: knowledgeBases });
	});
}

async function askQuestion(page: Page, question: string) {
	await page.goto("/qa");
	await page.getByPlaceholder("输入你想问的问题…").fill(question);
	await page.getByRole("button", { name: "提交问题" }).click();
}

test.describe("问答页引用版本提示", () => {
	test("顶部提供问答控制台快捷入口", async ({ page }) => {
		await mockQaShell(page);

		await page.goto("/knowledge");
		await expect(page.getByTestId("header-qa-entry")).toBeVisible();
		await page.getByTestId("header-qa-entry").click();
		await expect(page).toHaveURL(/\/qa$/);
	});

	test("展示引用卡片版本字段，并在存在 stale reference 时展示顶部提示", async ({
		page,
	}) => {
		await mockQaShell(page);
		await page.route("**/api/v1/qa/ask", async (route) => {
			await route.fulfill({
				json: {
					answer: "供应商准入需要完成资质审核。",
					references: [
						{
							documentId: "doc-policy-001",
							chunkIndex: 0,
							contentPreview: "供应商准入流程需要先完成资质审核。",
							sourceVersionNumber: 2,
							sourceUpdatedAt: "2026-05-09T10:00:00Z",
							isLatestVersion: false,
							latestVersionNumber: 3,
							sourceFilename: "supplier-policy-v2.pdf",
						},
						{
							documentId: "doc-policy-002",
							chunkIndex: 1,
							contentPreview: "付款条款以最新合同模板为准。",
							sourceVersionNumber: 4,
							sourceUpdatedAt: "2026-05-10T08:30:00Z",
							isLatestVersion: true,
							latestVersionNumber: 4,
							sourceFilename: "payment-template-v4.pdf",
						},
					],
					staleReferences: {
						hasStaleReferences: true,
						staleReferenceCount: 1,
						staleDocumentCount: 1,
						documents: [
							{
								documentId: "doc-policy-001",
								sourceVersionNumber: 2,
								latestVersionNumber: 3,
								sourceFilename: "supplier-policy-v2.pdf",
							},
						],
					},
				},
			});
		});

		await askQuestion(page, "供应商准入怎么做？");

		await expect(page.getByTestId("qa-stale-reference-banner")).toContainText(
			"本次回答包含 1 条非最新版本引用",
		);
		const staleCard = page.getByTestId("reference-card-doc-policy-001-0");
		await expect(staleCard).toContainText("supplier-policy-v2.pdf");
		await expect(staleCard).toContainText("doc-policy-001");
		await expect(staleCard).toContainText("v2");
		await expect(staleCard).toContainText("来源更新时间：");
		await expect(staleCard).toContainText("当前最新版本为 v3");

		const latestCard = page.getByTestId("reference-card-doc-policy-002-1");
		await expect(latestCard).toContainText("payment-template-v4.pdf");
		await expect(latestCard).toContainText("v4");
		await expect(
			page.getByTestId("reference-stale-doc-policy-002-1"),
		).toHaveCount(0);
	});

	test("没有 stale reference 时不展示顶部版本提示", async ({ page }) => {
		await mockQaShell(page);
		await page.route("**/api/v1/qa/ask", async (route) => {
			await route.fulfill({
				json: {
					answer: "付款条款以最新合同模板为准。",
					references: [
						{
							documentId: "doc-policy-002",
							chunkIndex: 1,
							contentPreview: "付款条款以最新合同模板为准。",
							sourceVersionNumber: 4,
							sourceUpdatedAt: "2026-05-10T08:30:00Z",
							isLatestVersion: true,
							latestVersionNumber: 4,
							sourceFilename: "payment-template-v4.pdf",
						},
					],
					staleReferences: {
						hasStaleReferences: false,
						staleReferenceCount: 0,
						staleDocumentCount: 0,
						documents: [],
					},
				},
			});
		});

		await askQuestion(page, "付款条款是什么？");

		await expect(page.getByTestId("qa-stale-reference-banner")).toHaveCount(0);
		await expect(page.getByTestId("reference-card-doc-policy-002-1")).toContainText(
			"payment-template-v4.pdf",
		);
	});

	test("无文档引用的兜底回答不展示版本提示", async ({ page }) => {
		await mockQaShell(page);
		await page.route("**/api/v1/qa/ask", async (route) => {
			await route.fulfill({
				json: {
					answer: "当前知识库未检索到相关内容。",
					references: [],
					staleReferences: null,
				},
			});
		});

		await askQuestion(page, "没有资料的问题");

		await expect(page.getByTestId("qa-stale-reference-banner")).toHaveCount(0);
		await expect(page.getByText("无命中")).toBeVisible();
		await expect(
			page.getByText("该问题未检索到匹配的文档分块，回答内容来自模型兜底。"),
		).toBeVisible();
	});
});
