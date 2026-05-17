import { Button, DatePicker, Form, Input, Select, Space, type FormInstance } from "antd";
import { ReloadOutlined, SearchOutlined } from "@ant-design/icons";

const { RangePicker } = DatePicker;

export const OUTCOME_OPTIONS = [
	{ label: "SUCCESS", value: "SUCCESS" },
	{ label: "FAILURE", value: "FAILURE" },
	{ label: "DENIED", value: "DENIED" },
];

interface AuditEventsFilterProps {
	form: FormInstance;
	onSearch: () => void;
	onReset: () => void;
	loading: boolean;
}

export function AuditEventsFilter({
	form,
	onSearch,
	onReset,
	loading,
}: AuditEventsFilterProps) {
	return (
		<Form
			form={form}
			layout="inline"
			style={{ flexWrap: "wrap", gap: "12px 8px" }}
			onFinish={onSearch}
		>
			<Form.Item name="eventType" style={{ minWidth: 160, margin: 0 }}>
				<Input allowClear placeholder="事件类型" />
			</Form.Item>
			<Form.Item name="actorKeyword" style={{ minWidth: 180, margin: 0 }}>
				<Input allowClear placeholder="操作者 (ID/用户名)" />
			</Form.Item>
			<Form.Item name="targetType" style={{ minWidth: 140, margin: 0 }}>
				<Input allowClear placeholder="目标类型" />
			</Form.Item>
			<Form.Item name="targetId" style={{ minWidth: 140, margin: 0 }}>
				<Input allowClear placeholder="目标ID" />
			</Form.Item>
			<Form.Item name="outcome" style={{ minWidth: 120, margin: 0 }}>
				<Select
					allowClear
					placeholder="执行结果"
					options={OUTCOME_OPTIONS}
				/>
			</Form.Item>
			<Form.Item name="dateRange" style={{ minWidth: 240, margin: 0 }}>
				<RangePicker allowClear placeholder={["开始日期", "结束日期"]} />
			</Form.Item>
			<Form.Item style={{ margin: 0 }}>
				<Space>
					<Button
						type="primary"
						htmlType="submit"
						icon={<SearchOutlined />}
						loading={loading}
					>
						查询
					</Button>
					<Button icon={<ReloadOutlined />} onClick={onReset}>
						重置
					</Button>
				</Space>
			</Form.Item>
		</Form>
	);
}
