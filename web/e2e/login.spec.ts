import { expect, test, type APIRequestContext } from "@playwright/test";
import {
	loginAsAdmin,
	prepareManagedAccount,
} from "./helpers/adminAuth";
import { LoginPage } from "./pages/LoginPage";

const activeUsername = process.env.PLAYWRIGHT_ACTIVE_USERNAME ?? "test_user2";
const activePassword = process.env.PLAYWRIGHT_ACTIVE_PASSWORD ?? "123456";
const disabledUsername =
	process.env.PLAYWRIGHT_DISABLED_USERNAME ?? "test_user1";
const disabledPassword =
	process.env.PLAYWRIGHT_DISABLED_PASSWORD ?? "123456";

test.describe("登录链路首批 E2E", () => {
	async function restoreManagedAccounts(request: APIRequestContext) {
		await loginAsAdmin(request);
		await prepareManagedAccount(request, {
			username: activeUsername,
			password: activePassword,
			userStatus: "ACTIVE",
		});
		await prepareManagedAccount(request, {
			username: disabledUsername,
			password: disabledPassword,
			userStatus: "DISABLED",
		});
	}

	test.beforeEach(async ({ request }) => {
		await restoreManagedAccounts(request);
	});

	test.afterEach(async ({ request }) => {
		await restoreManagedAccounts(request);
	});

	test("管理员登录成功后进入默认控制台落点", async ({ page }) => {
		const loginPage = new LoginPage(page);

		await test.step("打开登录页并提交管理员凭据", async () => {
			await loginPage.goto();
			await loginPage.login("admin", "admin");
		});

		await test.step("跳转到默认首页并展示控制台标题", async () => {
			await expect(page).toHaveURL(/\/ingest\/documents$/);
			await expect(
				page.getByRole("heading", { name: "文档列表与管理台" }),
			).toBeVisible();
		});
	});

	test("密码错误时展示明确失败提示", async ({ page }) => {
		const loginPage = new LoginPage(page);

		await loginPage.goto();
		await loginPage.login(activeUsername, "wrong-password");

		await expect(page).toHaveURL(/\/login$/);
		await loginPage.expectError("用户名或密码错误，请重新输入。");
	});

	test("停用账号使用正确密码登录时展示禁用提示", async ({ page }) => {
		const loginPage = new LoginPage(page);

		await loginPage.goto();
		await loginPage.login(disabledUsername, disabledPassword);

		await expect(page).toHaveURL(/\/login$/);
		await loginPage.expectError("账号已被禁用，请联系管理员。");
	});

	test("连续 5 次错误密码后触发锁定提示", async ({ page }) => {
		const loginPage = new LoginPage(page);

		await loginPage.goto();

		for (let attempt = 1; attempt <= 4; attempt += 1) {
			await test.step(`第 ${attempt} 次错密仍返回用户名或密码错误`, async () => {
				await loginPage.login(activeUsername, "wrong-password");
				await loginPage.expectError("用户名或密码错误，请重新输入。");
			});
		}

		await test.step("第 5 次错密触发锁定提示", async () => {
			await loginPage.login(activeUsername, "wrong-password");
			await loginPage.expectError(/账号已锁定，请于 .* 后重试。/);
		});
	});

	test("账号锁定后立即输入正确密码仍保持锁定", async ({ page }) => {
		const loginPage = new LoginPage(page);

		await loginPage.goto();

		for (let attempt = 1; attempt <= 4; attempt += 1) {
			await loginPage.login(activeUsername, "wrong-password");
			await loginPage.expectError("用户名或密码错误，请重新输入。");
		}

		await loginPage.login(activeUsername, "wrong-password");
		await loginPage.expectError(/账号已锁定，请于 .* 后重试。/);

		await test.step("锁定窗口内输入正确密码仍返回锁定提示", async () => {
			await loginPage.login(activeUsername, activePassword);
			await loginPage.expectError(/账号已锁定，请于 .* 后重试。/);
			await expect(page).toHaveURL(/\/login$/);
		});
	});
});
