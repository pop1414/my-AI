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
	await page.getByPlaceholder("请输入您的问题...").fill(question);
	await page.getByRole("button", { name: "发送" }).click();
}

test.describe("问答页引用版本提示", () => {
	test("知识库页可以进入问答聊天页", async ({ page }) => {
		await mockQaShell(page);

		await page.goto("/knowledge");
		await expect(
			page.getByRole("link", { name: "进入问答验证" }).first(),
		).toBeVisible();
		await page.getByRole("link", { name: "进入问答验证" }).first().click();
		await expect(page).toHaveURL(/\/qa(\?kbId=default)?$/);
	});

	test("聊天页保留知识库侧栏和当前上下文", async ({ page }) => {
		await mockQaShell(page);

		await page.goto("/qa");

		await expect(page.getByTestId("qa-kb-sidebar")).toContainText("选择知识库");
		await expect(page.getByTestId("qa-kb-default")).toContainText("默认知识库");
		await expect(page.getByTestId("qa-context-strip")).toContainText("正在查询");
		await expect(page.getByTestId("qa-context-strip")).toContainText("默认知识库");
		await expect(page.getByTestId("qa-chat-list")).toContainText(
			"开始一轮新问答",
		);
	});

	test("回答后可以查看引用片段和问答基线文档", async ({ page }) => {
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
		await expect(page.getByTestId("qa-chat-list")).toContainText(
			"供应商准入需要完成资质审核。",
		);
		await expect(page.getByTestId("qa-stale-reference-banner")).toContainText(
			"包含 1 条非最新版本引用",
		);
		await expect(page.getByTestId("reference-card-doc-policy-001-0")).toContainText(
			"supplier-policy-v2.pdf",
		);
		await expect(page.getByTestId("reference-card-doc-policy-001-0")).toContainText(
			"供应商准入流程需要先完成资质审核。",
		);

		await page.getByTestId("reference-card-doc-policy-001-0").click();
		await expect(page.getByTestId("qa-reference-drawer")).toContainText(
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

	test("无文档引用时展示模型兜底提示", async ({ page }) => {
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

		await expect(page.getByTestId("qa-chat-list")).toContainText(
			"当前知识库未检索到相关内容。",
		);
		await expect(page.getByText("当前回答没有命中文档引用，内容来自模型兜底。")).toBeVisible();
	});
});
