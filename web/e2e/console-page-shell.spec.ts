import { expect, test, type Page, type Route } from "@playwright/test";

const fullAccessUser = {
	userId: "user-shell",
	username: "shell_user",
	displayName: "控制台用户",
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

const knowledgeBases = [
	{
		id: "default",
		name: "默认知识库",
		description: "默认问答知识库",
		status: "ACTIVE",
		indexedDocumentCount: 12,
	},
	{
		id: "governance",
		name: "治理知识库",
		description: "流程与规范沉淀",
		status: "INACTIVE",
		indexedDocumentCount: 4,
	},
];

async function mockConsoleShell(
	page: Page,
	options?: {
		knowledgeHandler?: (route: Route) => Promise<void>;
	},
) {
	await page.route("**/api/v1/auth/me", async (route) => {
		await route.fulfill({ json: fullAccessUser });
	});
	await page.route("**/api/v1/knowledge-bases**", async (route) => {
		if (options?.knowledgeHandler) {
			await options.knowledgeHandler(route);
			return;
		}
		await route.fulfill({ json: knowledgeBases });
	});
}

test.describe("控制台共享页面骨架", () => {
	test("模块导航按稳定域分组，知识库页渲染统一骨架", async ({ page }) => {
		await mockConsoleShell(page);

		await page.goto("/knowledge");

		await expect(page.getByTestId("console-module-documents")).toBeVisible();
		await expect(page.getByTestId("console-module-knowledge")).toBeVisible();
		await expect(page.getByTestId("console-module-qa")).toBeVisible();
		await expect(page.getByTestId("console-module-admin")).toBeVisible();
		await expect(page.getByTestId("console-page-header")).toBeVisible();
		await expect(page.getByTestId("console-page-summary")).toBeVisible();
		await expect(page.getByTestId("console-page-workspace")).toBeVisible();
		await expect(page.getByTestId("console-page-status")).toBeVisible();
		await expect(page.getByTestId("knowledge-query-status")).toContainText(
			"知识库目录已就绪",
		);
	});

	test("loading 状态使用统一状态卡表达", async ({ page }) => {
		await mockConsoleShell(page, {
			knowledgeHandler: async (route) => {
				await new Promise((resolve) => setTimeout(resolve, 1200));
				await route.fulfill({ json: knowledgeBases });
			},
		});

		await page.goto("/knowledge");

		await expect(page.getByTestId("knowledge-list-state")).toContainText(
			"正在同步知识库目录",
		);
	});

	test("empty 状态使用统一状态卡表达", async ({ page }) => {
		await mockConsoleShell(page, {
			knowledgeHandler: async (route) => {
				await route.fulfill({ json: [] });
			},
		});

		await page.goto("/knowledge");

		await expect(page.getByTestId("knowledge-list-state")).toContainText(
			"尚未配置知识库",
		);
		await expect(page.getByTestId("knowledge-query-status")).toContainText(
			"当前工作区没有可承接问答的知识库",
		);
	});

	test("error 状态使用统一状态卡表达", async ({ page }) => {
		await mockConsoleShell(page, {
			knowledgeHandler: async (route) => {
				await route.fulfill({
					status: 500,
					contentType: "application/json",
					body: JSON.stringify({ message: "知识库查询失败" }),
				});
			},
		});

		await page.goto("/knowledge");

		await expect(page.getByTestId("knowledge-list-state")).toContainText(
			"知识库目录暂不可用",
		);
		await expect(page.getByTestId("knowledge-list-state")).toContainText(
			"请求失败（500）：知识库查询失败",
		);
	});
});
