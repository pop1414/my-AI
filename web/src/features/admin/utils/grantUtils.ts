export const kbRoleOptions = [
	{ label: "管理者 (KB_MANAGER)", value: "KB_MANAGER" },
	{ label: "贡献者 (KB_CONTRIBUTOR)", value: "KB_CONTRIBUTOR" },
	{ label: "读者 (KB_READER)", value: "KB_READER" },
];

export const documentPermissionOptions = [
	{ label: "允许读取 (DOC_ALLOW_READ)", value: "DOC_ALLOW_READ" },
	{ label: "允许管理 (DOC_ALLOW_MANAGE)", value: "DOC_ALLOW_MANAGE" },
	{ label: "显式拒绝 (DOC_DENY)", value: "DOC_DENY" },
];

export function toggleChecked(
	currentValues: string[],
	targetValue: string,
	checked: boolean,
): string[] {
	if (checked) {
		return currentValues.includes(targetValue)
			? currentValues
			: [...currentValues, targetValue];
	}
	return currentValues.filter((item) => item !== targetValue);
}
