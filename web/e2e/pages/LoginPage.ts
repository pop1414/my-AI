import { expect, type Locator, type Page } from "@playwright/test";

export class LoginPage {
	readonly page: Page;
	readonly usernameInput: Locator;
	readonly passwordInput: Locator;
	readonly submitButton: Locator;

	constructor(page: Page) {
		this.page = page;
		this.usernameInput = page.getByPlaceholder("用户名");
		this.passwordInput = page.getByPlaceholder("密码");
		this.submitButton = page.getByRole("button", { name: /登\s*录/ });
	}

	async goto() {
		await this.page.goto("/login");
		await expect(
			this.page.getByRole("heading", { name: "my-AI" }),
		).toBeVisible();
		await expect(this.usernameInput).toBeVisible();
	}

	async login(username: string, password: string) {
		await this.usernameInput.fill(username);
		await this.passwordInput.fill(password);
		await this.submitButton.click();
	}

	async expectError(message: string | RegExp) {
		await expect(this.page.getByText(message)).toBeVisible();
	}
}
