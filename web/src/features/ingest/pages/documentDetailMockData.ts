export type VersionOriginType = "UPLOAD" | "ROLLBACK";

export type PrototypeVersion = {
	versionNumber: number;
	versionOriginType: VersionOriginType;
	rollbackFromVersionNumber?: number;
	filename: string;
	fileSize: number;
	status: "INDEXED" | "FAILED" | "INGESTING";
	updatedAt: string;
	createdAt: string;
	createdByDisplayName: string;
	isLatestVersion: boolean;
	isAskableVersion: boolean;
	hasBeenRolledBackAsLatest: boolean;
	canRollback: boolean;
	summary: string;
	note: string;
};

export type PrototypeDocument = {
	documentId: string;
	kbId: string;
	title: string;
	latestVersionNumber: number;
	askableVersionNumber: number;
	latestStatus: string;
	latestVersionOriginType: VersionOriginType;
	versions: PrototypeVersion[];
};

export function formatTime(iso: string): string {
	return new Date(iso).toLocaleString("zh-CN", {
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
	});
}

export function formatFileSize(fileSize: number): string {
	if (fileSize >= 1024 * 1024) {
		return `${(fileSize / (1024 * 1024)).toFixed(1)} MB`;
	}
	return `${(fileSize / 1024).toFixed(0)} KB`;
}

export function statusColor(status: PrototypeVersion["status"]): string {
	switch (status) {
		case "INDEXED":
			return "success";
		case "FAILED":
			return "error";
		case "INGESTING":
			return "processing";
		default:
			return "default";
	}
}

export function buildDocumentDetailMockData(
	documentId: string,
): PrototypeDocument {
	return {
		documentId,
		kbId: "kb-policy-lab",
		title: "采购制度汇编（2026 版）",
		latestVersionNumber: 7,
		askableVersionNumber: 6,
		latestStatus: "INGESTING",
		latestVersionOriginType: "UPLOAD",
		versions: [
			{
				versionNumber: 7,
				versionOriginType: "UPLOAD",
				filename: "procurement-policy-2026-rewrite.docx",
				fileSize: 1_640_000,
				status: "INGESTING",
				updatedAt: "2026-05-12T21:35:00+08:00",
				createdAt: "2026-05-12T21:21:00+08:00",
				createdByDisplayName: "Ayanami",
				isLatestVersion: true,
				isAskableVersion: false,
				hasBeenRolledBackAsLatest: false,
				canRollback: false,
				summary: "新最新版本已上传，解析仍在进行中。",
				note: "详情页主视图应落在 v7，但问答仍继续使用最近一个已 INDEXED 的版本。",
			},
			{
				versionNumber: 6,
				versionOriginType: "UPLOAD",
				filename: "procurement-policy-2026.docx",
				fileSize: 1_590_000,
				status: "INDEXED",
				updatedAt: "2026-05-03T10:16:00+08:00",
				createdAt: "2026-05-03T09:42:00+08:00",
				createdByDisplayName: "Ayanami",
				isLatestVersion: false,
				isAskableVersion: true,
				hasBeenRolledBackAsLatest: false,
				canRollback: true,
				summary: "当前问答基线版本。",
				note: "最新版本未可问答时，问答入口应明确提示仍使用 v6。",
			},
			{
				versionNumber: 5,
				versionOriginType: "UPLOAD",
				filename: "procurement-policy-2026-beta.docx",
				fileSize: 1_520_000,
				status: "FAILED",
				updatedAt: "2026-04-17T17:44:00+08:00",
				createdAt: "2026-04-17T17:10:00+08:00",
				createdByDisplayName: "Ayanami",
				isLatestVersion: false,
				isAskableVersion: false,
				hasBeenRolledBackAsLatest: false,
				canRollback: false,
				summary: "一次失败的最新版本尝试。",
				note: "失败版本仍属于版本链事实，但不能作为回退目标。",
			},
			{
				versionNumber: 4,
				versionOriginType: "UPLOAD",
				filename: "procurement-master-policy.docx",
				fileSize: 1_480_000,
				status: "INDEXED",
				updatedAt: "2026-04-01T11:23:00+08:00",
				createdAt: "2026-04-01T10:38:00+08:00",
				createdByDisplayName: "Li Wei",
				isLatestVersion: false,
				isAskableVersion: false,
				hasBeenRolledBackAsLatest: false,
				canRollback: true,
				summary: "引入新文件名的一次正式迭代。",
				note: "适合拿来验证历史查看态与差异摘要区的表达效果。",
			},
			{
				versionNumber: 3,
				versionOriginType: "ROLLBACK",
				rollbackFromVersionNumber: 1,
				filename: "procurement-policy-v1.docx",
				fileSize: 1_210_000,
				status: "INDEXED",
				updatedAt: "2026-03-15T14:08:00+08:00",
				createdAt: "2026-03-15T13:56:00+08:00",
				createdByDisplayName: "Chen Lin",
				isLatestVersion: false,
				isAskableVersion: false,
				hasBeenRolledBackAsLatest: false,
				canRollback: true,
				summary: "一次由历史版本回退产生的新最新版本。",
				note: "它本身既是最新版本历史上的一个节点，也是“回退产生”的显式样本。",
			},
			{
				versionNumber: 2,
				versionOriginType: "UPLOAD",
				filename: "procurement-policy-v2.docx",
				fileSize: 1_280_000,
				status: "INDEXED",
				updatedAt: "2026-02-24T18:19:00+08:00",
				createdAt: "2026-02-24T17:50:00+08:00",
				createdByDisplayName: "Chen Lin",
				isLatestVersion: false,
				isAskableVersion: false,
				hasBeenRolledBackAsLatest: false,
				canRollback: true,
				summary: "规则章节扩写后的稳定版本。",
				note: "适合观察长版本链折叠后如何保持可扫描性。",
			},
			{
				versionNumber: 1,
				versionOriginType: "UPLOAD",
				filename: "procurement-policy-v1.docx",
				fileSize: 1_210_000,
				status: "INDEXED",
				updatedAt: "2026-02-02T09:14:00+08:00",
				createdAt: "2026-02-02T08:42:00+08:00",
				createdByDisplayName: "Chen Lin",
				isLatestVersion: false,
				isAskableVersion: false,
				hasBeenRolledBackAsLatest: true,
				canRollback: true,
				summary: "初始入库版本。",
				note: "曾在 v3 被回退为最新版本，因此应标记“曾回退为最新版本”。",
			},
		],
	};
}
