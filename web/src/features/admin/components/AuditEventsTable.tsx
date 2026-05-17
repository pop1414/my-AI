import { Table, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { formatTime } from "../../ingest/utils/formatters";
import type { AuditEvent } from "../../../shared/api/adminApi";

const outcomeColorMap: Record<string, string> = {
	SUCCESS: "success",
	FAILURE: "error",
	DENIED: "warning",
};

interface AuditEventsTableProps {
	loading: boolean;
	data: AuditEvent[];
	total: number;
	page: number;
	pageSize: number;
	onPageChange: (page: number) => void;
	hasFilters: boolean;
}

export function AuditEventsTable({
	loading,
	data,
	total,
	page,
	pageSize,
	onPageChange,
	hasFilters,
}: AuditEventsTableProps) {
	const columns: ColumnsType<AuditEvent> = [
		{
			title: "事件 ID",
			dataIndex: "auditEventId",
			width: 100,
			render: (val) => (
				<Typography.Text type="secondary" style={{ fontSize: 12, fontFamily: 'var(--console-font-mono)' }}>
					{val}
				</Typography.Text>
			)
		},
		{
			title: "事件类型",
			dataIndex: "eventType",
			width: 180,
			ellipsis: true,
			render: (val) => <span style={{ fontWeight: 500 }}>{val}</span>
		},
		{
			title: "操作者",
			dataIndex: "actorUsername",
			width: 140,
			render: (val) => val ? (
				<Typography.Text style={{ fontFamily: 'var(--console-font-mono)', fontSize: 13 }}>@{val}</Typography.Text>
			) : "-"
		},
		{
			title: "目标 (类型/ID)",
			width: 220,
			render: (_, record) => (
				<div style={{ display: 'flex', flexDirection: 'column' }}>
					<Typography.Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase' }}>{record.targetType || '-'}</Typography.Text>
					<Typography.Text style={{ fontSize: 12, fontFamily: 'var(--console-font-mono)' }} ellipsis title={record.targetId}>{record.targetId || '-'}</Typography.Text>
				</div>
			)
		},
		{
			title: "执行结果",
			dataIndex: "outcome",
			width: 100,
			render: (val: string) => (
				<Tag bordered={false} color={outcomeColorMap[val] ?? "default"} style={{ borderRadius: 4, fontWeight: 500 }}>
					{val}
				</Tag>
			),
		},
		{
			title: "原因/详情",
			dataIndex: "reason",
			width: 200,
			ellipsis: true,
			render: (val) => val ? <Tooltip title={val}>{val}</Tooltip> : <Typography.Text type="disabled">-</Typography.Text>
		},
		{
			title: "时间",
			dataIndex: "occurredAt",
			width: 160,
			render: (val: string) => (
				<span style={{ fontSize: 12, color: 'var(--console-muted)' }}>
					{formatTime(val)}
				</span>
			),
		},
	];

	return (
		<Table
			columns={columns}
			dataSource={data}
			rowKey="auditEventId"
			loading={loading}
			size="small"
			scroll={{ x: 1100 }}
			pagination={{
				current: page,
				pageSize: pageSize,
				total: total,
				showTotal: (t) => `共 ${t} 条记录`,
				showSizeChanger: false,
				onChange: onPageChange,
			}}
			locale={{
				emptyText: hasFilters
					? "未找到符合当前筛选条件的审计记录"
					: "暂无审计记录",
			}}
		/>
	);
}
