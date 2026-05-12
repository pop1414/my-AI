import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
	Button,
	Card,
	DatePicker,
	Form,
	Input,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import { ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { listAuditEvents, type AuditEvent } from "../../../shared/api/adminApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import type { ApiError } from "../../../shared/api/request";

const { Title } = Typography;
const { RangePicker } = DatePicker;

const outcomeColorMap: Record<string, string> = {
	SUCCESS: "success",
	FAILURE: "error",
	DENIED: "warning",
};

const OUTCOME_OPTIONS = [
	{ label: "SUCCESS", value: "SUCCESS" },
	{ label: "FAILURE", value: "FAILURE" },
	{ label: "DENIED", value: "DENIED" },
];

const PAGE_SIZE = 20;

function formatTime(iso: string): string {
	try {
		return new Date(iso).toLocaleString("zh-CN", {
			year: "numeric",
			month: "2-digit",
			day: "2-digit",
			hour: "2-digit",
			minute: "2-digit",
			second: "2-digit",
		});
	} catch {
		return iso;
	}
}

export function AuditEventsPage() {
	const [filters, setFilters] = useState<{
		eventType?: string;
		actorKeyword?: string;
		targetType?: string;
		targetId?: string;
		outcome?: "SUCCESS" | "FAILURE" | "DENIED";
		dateRange?: [unknown, unknown];
	}>({});
	const [page, setPage] = useState(1);
	const [form] = Form.useForm();

	const buildQueryParams = () => {
		const params: Record<string, unknown> = {
			limit: PAGE_SIZE,
			offset: (page - 1) * PAGE_SIZE,
		};
		if (filters.eventType) params.eventType = filters.eventType;
		if (filters.actorKeyword) params.actorKeyword = filters.actorKeyword;
		if (filters.targetType) params.targetType = filters.targetType;
		if (filters.targetId) params.targetId = filters.targetId;
		if (filters.outcome) params.outcome = filters.outcome;
		if (filters.dateRange) {
			// Ant Design RangePicker 使用 dayjs，此处取其 startOf/endOf 方法
			// eslint-disable-next-line @typescript-eslint/no-explicit-any
			const [start, end] = filters.dateRange as any[];
			if (start && end) {
				params.occurredFrom = start.startOf("day").toISOString();
				params.occurredTo = end.endOf("day").toISOString();
			}
		}
		return params;
	};

	const auditQuery = useQuery({
		queryKey: ["admin", "audit-events", filters, page],
		queryFn: () => listAuditEvents(buildQueryParams()),
	});

	const columns: ColumnsType<AuditEvent> = [
		{
			title: "事件ID",
			dataIndex: "auditEventId",
			width: 80,
		},
		{
			title: "事件类型",
			dataIndex: "eventType",
			width: 200,
			ellipsis: true,
		},
		{
			title: "操作者",
			dataIndex: "actorUsername",
			width: 140,
			render: (value: string | null | undefined) => value ?? "-",
		},
		{
			title: "目标类型",
			dataIndex: "targetType",
			width: 160,
			ellipsis: true,
			render: (value: string | null | undefined) => value ?? "-",
		},
		{
			title: "目标ID",
			dataIndex: "targetId",
			width: 160,
			ellipsis: true,
			render: (value: string | null | undefined) => value ?? "-",
		},
		{
			title: "结果",
			dataIndex: "outcome",
			width: 100,
			render: (value: string) => (
				<Tag color={outcomeColorMap[value] ?? "default"}>{value}</Tag>
			),
		},
		{
			title: "原因",
			dataIndex: "reason",
			width: 200,
			ellipsis: true,
		},
		{
			title: "时间",
			dataIndex: "occurredAt",
			width: 160,
			render: (value: string) => formatTime(value),
		},
	];

	const dataSource = auditQuery.data?.items ?? [];
	const total = auditQuery.data?.total ?? 0;
	const apiError = auditQuery.error as ApiError | null;

	const onSearch = () => {
		const values = form.getFieldsValue();
		setFilters({
			eventType: values.eventType || undefined,
			actorKeyword: values.actorKeyword || undefined,
			targetType: values.targetType || undefined,
			targetId: values.targetId || undefined,
			outcome: values.outcome || undefined,
			dateRange: values.dateRange || undefined,
		});
		setPage(1);
	};

	const onReset = () => {
		form.resetFields();
		setFilters({});
		setPage(1);
	};

	return (
		<div>
			<Title level={4} style={{ marginBottom: 16 }}>
				审计日志
			</Title>

			{/* 筛选区域 */}
			<Card style={{ marginBottom: 16 }}>
				<Form
					form={form}
					layout="inline"
					style={{ flexWrap: "wrap", gap: 8 }}
					onFinish={onSearch}
				>
					<Form.Item name="eventType" style={{ minWidth: 180 }}>
						<Input allowClear placeholder="事件类型" />
					</Form.Item>
					<Form.Item name="actorKeyword" style={{ minWidth: 200 }}>
						<Input allowClear placeholder="操作者用户名或ID" />
					</Form.Item>
					<Form.Item name="targetType" style={{ minWidth: 160 }}>
						<Input allowClear placeholder="目标类型" />
					</Form.Item>
					<Form.Item name="targetId" style={{ minWidth: 160 }}>
						<Input allowClear placeholder="目标ID" />
					</Form.Item>
					<Form.Item name="outcome" style={{ minWidth: 130 }}>
						<Select
							allowClear
							placeholder="结果"
							options={OUTCOME_OPTIONS}
						/>
					</Form.Item>
					<Form.Item name="dateRange" style={{ minWidth: 260 }}>
						<RangePicker
							allowClear
							placeholder={["开始日期", "结束日期"]}
						/>
					</Form.Item>
					<Form.Item>
						<Space>
							<Button
								type="primary"
								htmlType="submit"
								icon={<SearchOutlined />}
								loading={auditQuery.isFetching}
							>
								查询
							</Button>
							<Button icon={<ReloadOutlined />} onClick={onReset}>
								重置
							</Button>
						</Space>
					</Form.Item>
				</Form>
			</Card>

			{apiError && (
				<div style={{ marginBottom: 16 }}>
					<ApiErrorAlert error={apiError} />
				</div>
			)}

			<Table<AuditEvent>
				columns={columns}
				dataSource={dataSource}
				rowKey="auditEventId"
				loading={auditQuery.isLoading}
				pagination={{
					current: page,
					pageSize: PAGE_SIZE,
					total,
					showTotal: (t) => `共 ${t} 条`,
					showSizeChanger: false,
					onChange: (p) => setPage(p),
				}}
				locale={{
					emptyText:
						Object.values(filters).some(Boolean)
							? "未找到符合当前筛选条件的审计记录，请尝试放宽条件。"
							: "暂无审计记录",
				}}
			/>
		</div>
	);
}
