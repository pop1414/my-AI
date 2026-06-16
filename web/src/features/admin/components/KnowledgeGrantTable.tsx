import { Button, Checkbox, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { KnowledgeBaseStatusTag } from "../../knowledge/components/KnowledgeBaseStatusTag";
import { kbRoleOptions, toggleChecked } from "../utils/grantUtils";
import type { KnowledgeBase } from "../../../shared/api/knowledgeApi";
import type { KnowledgeBaseGrant } from "../../../shared/api/adminApi";

interface KnowledgeGrantTableProps {
	loading: boolean;
	data: KnowledgeBase[];
	selectedIds: string[];
	roles: Record<string, KnowledgeBaseGrant["role"]>;
	onSelectionChange: (ids: string[]) => void;
	onRoleChange: (roles: Record<string, KnowledgeBaseGrant["role"]>) => void;
}

export function KnowledgeGrantTable({
	loading,
	data,
	selectedIds,
	roles,
	onSelectionChange,
	onRoleChange,
}: KnowledgeGrantTableProps) {
	const checkAll = data.length > 0 && selectedIds.length === data.length;
	const indeterminate = selectedIds.length > 0 && selectedIds.length < data.length;

	const columns: ColumnsType<KnowledgeBase> = [
		{
			title: "授权",
			dataIndex: "id",
			width: 80,
			render: (kbId: string) => (
				<Checkbox
					checked={selectedIds.includes(kbId)}
					onChange={(e) => {
						const next = toggleChecked(selectedIds, kbId, e.target.checked);
						onSelectionChange(next);
						if (e.target.checked && !roles[kbId]) {
							onRoleChange({ ...roles, [kbId]: "KB_READER" });
						}
					}}
				/>
			),
		},
		{
			title: "知识库名称",
			dataIndex: "name",
			width: 220,
			render: (val) => <span style={{ fontWeight: 500 }}>{val}</span>
		},
		{
			title: "知识库 ID",
			dataIndex: "id",
			width: 200,
			render: (val) => (
				<Typography.Text type="secondary" style={{ fontSize: 12, fontFamily: 'var(--console-font-mono)' }}>
					{val}
				</Typography.Text>
			)
		},
		{
			title: "授权角色",
			dataIndex: "id",
			width: 200,
			render: (kbId: string) => (
				<Select
					style={{ width: "100%" }}
					disabled={!selectedIds.includes(kbId)}
					value={roles[kbId] ?? "KB_READER"}
					options={kbRoleOptions}
					onChange={(value: KnowledgeBaseGrant["role"]) =>
						onRoleChange({ ...roles, [kbId]: value })
					}
				/>
			),
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (value: KnowledgeBase["status"]) => <KnowledgeBaseStatusTag status={value} />,
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
							onSelectionChange(data.map(item => item.id));
							const nextRoles = { ...roles };
							data.forEach(item => {
								if (!nextRoles[item.id]) nextRoles[item.id] = "KB_READER";
							});
							onRoleChange(nextRoles);
						} else {
							onSelectionChange([]);
						}
					}}
				>
					本页全选
				</Checkbox>
				<Button size="small" onClick={() => onSelectionChange([])}>清空选择</Button>
			</Space>
			<Table
				rowKey="id"
				columns={columns}
				dataSource={data}
				pagination={false}
				loading={loading}
				size="small"
			/>
		</Space>
	);
}
