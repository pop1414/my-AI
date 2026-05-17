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
	await page.getByPlaceholder("提问或继续追问").fill(question);
	await page.getByRole("button", { name: "发送" }).click();
}

test.describe("问答页证据查验面板", () => {
	test("知识库页可以进入问答页", async ({ page }) => {
		await mockQaShell(page);

		await page.goto("/knowledge");
		await expect(
			page.getByRole("link", { name: "进入问答验证" }).first(),
		).toBeVisible();
		await page.getByRole("link", { name: "进入问答验证" }).first().click();
		await expect(page).toHaveURL(/\/qa(\?kbId=default)?$/);
	});

	test("默认展示三栏结构和右侧证据概览", async ({ page }) => {
		await mockQaShell(page);

		await page.goto("/qa");

		await expect(page.getByTestId("qa-kb-sidebar")).toContainText("来源");
		await expect(page.getByTestId("qa-context-strip")).toContainText("当前知识库");
		await expect(page.getByTestId("qa-context-strip")).toContainText("默认知识库");
		await expect(page.getByTestId("qa-chat-list")).toContainText("开始提问");
		await expect(page.getByTestId("qa-context-panel")).toContainText("证据查验");
		await expect(page.getByTestId("qa-context-panel")).toContainText("已索引文档");
		await expect(page.getByTestId("qa-context-panel")).toContainText("知识库摘要");
	});

	test("点击引用标签后右侧切换到证据面板并可打开问答基线文档", async ({
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

		await expect(page.getByTestId("qa-chat-list")).toContainText(
			"供应商准入怎么做？",
		);
		await expect(page.getByTestId("qa-stale-reference-banner")).toContainText(
			"包含 1 条非最新版本引用",
		);

		await page.getByTestId("reference-card-doc-policy-001-0").click();
		await expect(page.getByTestId("qa-reference-panel")).toContainText(
			"supplier-policy-v2.pdf",
		);
		await expect(page.getByTestId("qa-reference-panel")).toContainText(
			"供应商准入流程需要先完成资质审核。",
		);
		await expect(page.getByTestId("qa-open-baseline-doc")).toContainText(
			"查看问答基线文档",
		);

		await page.getByTestId("qa-open-baseline-doc").click();
		await expect(page).toHaveURL(
			/\/ingest\/documents\/doc-policy-001\/versions\/2\/read\?mode=single$/,
		);
	});

	test("可以切到参数面板并调整 Top-K", async ({ page }) => {
		await mockQaShell(page);

		await page.goto("/qa");
		await page.getByRole("button", { name: "参数" }).click();

		await expect(page.getByTestId("qa-context-panel")).toContainText(
			"RAG 参数调节",
		);
		await expect(page.getByLabel("Top-K")).toHaveValue("5");
		await page.getByLabel("Top-K").fill("8");
		await expect(page.getByLabel("Top-K")).toHaveValue("8");
		await expect(page.getByTestId("qa-context-panel")).toContainText(
			"当前 ask 接口还没有开放 temperature 参数",
		);
	});
});
