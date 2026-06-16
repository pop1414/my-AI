import { Button, Checkbox, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { DocumentStatusTag } from "../../ingest/components/DocumentStatusTag";
import { documentPermissionOptions, toggleChecked } from "../utils/grantUtils";
import type { DocumentListItem } from "../../../shared/api/ingestApi";
import type { DocumentGrant } from "../../../shared/api/adminApi";

interface DocumentGrantTableProps {
	loading: boolean;
	data: DocumentListItem[];
	total: number;
	page: number;
	pageSize: number;
	selectedIds: string[];
	permissions: Record<string, DocumentGrant["permission"]>;
	onPageChange: (page: number, pageSize: number) => void;
	onSelectionChange: (ids: string[]) => void;
	onPermissionChange: (perms: Record<string, DocumentGrant["permission"]>) => void;
}

export function DocumentGrantTable({
	loading,
	data,
	total,
	page,
	pageSize,
	selectedIds,
	permissions,
	onPageChange,
	onSelectionChange,
	onPermissionChange,
}: DocumentGrantTableProps) {
	const currentIds = data.map(item => item.documentId);
	const visibleSelected = currentIds.filter(id => selectedIds.includes(id));
	const checkAll = currentIds.length > 0 && visibleSelected.length === currentIds.length;
	const indeterminate = visibleSelected.length > 0 && visibleSelected.length < currentIds.length;

	const columns: ColumnsType<DocumentListItem> = [
		{
			title: "授权",
			dataIndex: "documentId",
			width: 80,
			render: (id: string) => (
				<Checkbox
					checked={selectedIds.includes(id)}
					onChange={(e) => {
						const next = toggleChecked(selectedIds, id, e.target.checked);
						onSelectionChange(next);
						if (e.target.checked) {
							if (!permissions[id]) {
								onPermissionChange({ ...permissions, [id]: "DOC_ALLOW_READ" });
							}
						} else {
							const { [id]: _unused, ...rest } = permissions; // eslint-disable-line @typescript-eslint/no-unused-vars -- 解构排除
							onPermissionChange(rest);
						}
					}}
				/>
			),
		},
		{
			title: "文件名",
			dataIndex: "filename",
			width: 260,
			ellipsis: true,
			render: (val) => <span style={{ fontWeight: 500 }}>{val}</span>
		},
		{
			title: "文档 ID",
			dataIndex: "documentId",
			width: 200,
			render: (val) => (
				<Typography.Text type="secondary" style={{ fontSize: 12, fontFamily: 'var(--console-font-mono)' }}>
					{val}
				</Typography.Text>
			)
		},
		{
			title: "文档权限",
			dataIndex: "documentId",
			width: 200,
			render: (id: string) => (
				<Select
					style={{ width: "100%" }}
					disabled={!selectedIds.includes(id)}
					value={permissions[id] ?? "DOC_ALLOW_READ"}
					options={documentPermissionOptions}
					onChange={(value: DocumentGrant["permission"]) =>
						onPermissionChange({ ...permissions, [id]: value })
					}
				/>
			),
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (val) => <DocumentStatusTag status={val} />,
		},
	];

	return (
		<Space direction="vertical" size={12} style={{ width: "100%" }}>
			<Space wrap>
				<Checkbox
					checked={checkAll}
					indeterminate={indeterminate}
					onChange={(e) => {
						if (e.target.checked) {
							const next = new Set(selectedIds);
							const nextPerms = { ...permissions };
							currentIds.forEach(id => {
								next.add(id);
								if (!nextPerms[id]) nextPerms[id] = "DOC_ALLOW_READ";
							});
							onSelectionChange(Array.from(next));
							onPermissionChange(nextPerms);
						} else {
							onSelectionChange(selectedIds.filter(id => !currentIds.includes(id)));
							const nextPerms = { ...permissions };
							currentIds.forEach(id => {
								delete nextPerms[id];
							});
							onPermissionChange(nextPerms);
						}
					}}
				>
					本页全选
				</Checkbox>
				<Button 
					size="small" 
					onClick={() => {
						onSelectionChange(selectedIds.filter(id => !currentIds.includes(id)));
						const nextPerms = { ...permissions };
						currentIds.forEach(id => {
							delete nextPerms[id];
						});
						onPermissionChange(nextPerms);
					}}
				>
					清空本页
				</Button>
			</Space>
			<Table
				rowKey="documentId"
				columns={columns}
				dataSource={data}
				loading={loading}
				scroll={{ x: 1000 }}
				size="small"
				pagination={{
					current: page,
					pageSize: pageSize,
					total: total,
					showSizeChanger: true,
					onChange: onPageChange,
				}}
			/>
		</Space>
	);
}
