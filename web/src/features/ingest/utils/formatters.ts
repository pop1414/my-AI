const fileSizeFormatter = new Intl.NumberFormat("zh-CN", {
	maximumFractionDigits: 1,
});

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
		return `${fileSizeFormatter.format(fileSize / (1024 * 1024))}\u00a0MB`;
	}
	if (fileSize >= 1024) {
		return `${fileSizeFormatter.format(fileSize / 1024)}\u00a0KB`;
	}
	return `${fileSizeFormatter.format(fileSize)}\u00a0B`;
}

export function originLabel(originType: string): string {
	switch (originType) {
		case "UPLOAD":
			return "上传产生";
		case "ROLLBACK":
			return "回退产生";
		default:
			return originType;
	}
}

export function statusColor(status: string): string {
	switch (status) {
		case "UPLOADED":
			return "blue";
		case "INGESTING":
		case "PROCESSING":
			return "processing";
		case "INDEXED":
			return "success";
		case "FAILED":
			return "error";
		case "DELETING":
			return "warning";
		case "DELETED":
			return "default";
		default:
			return "default";
	}
}
