import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Card, Form, Typography } from "antd";
import { listAuditEvents } from "../../../shared/api/adminApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import type { ApiError } from "../../../shared/api/request";

import { AuditEventsFilter } from "../components/AuditEventsFilter";
import { AuditEventsTable } from "../components/AuditEventsTable";

const { Title } = Typography;
const PAGE_SIZE = 20;

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

	const apiError = auditQuery.error as ApiError | null;
	const hasFilters = Object.values(filters).some(Boolean);

	return (
		<div className="audit-page">
			<Title level={4} style={{ marginBottom: 20, fontWeight: 500 }}>
				系统审计日志
			</Title>

			<Card size="small" style={{ marginBottom: 16 }}>
				<AuditEventsFilter
					form={form}
					onSearch={onSearch}
					onReset={onReset}
					loading={auditQuery.isFetching}
				/>
			</Card>

			{apiError && (
				<div style={{ marginBottom: 16 }}>
					<ApiErrorAlert error={apiError} />
				</div>
			)}

			<Card size="small" bodyStyle={{ padding: 0 }}>
				<AuditEventsTable
					loading={auditQuery.isLoading}
					data={auditQuery.data?.items ?? []}
					total={auditQuery.data?.total ?? 0}
					page={page}
					pageSize={PAGE_SIZE}
					onPageChange={setPage}
					hasFilters={hasFilters}
				/>
			</Card>
		</div>
	);
}

