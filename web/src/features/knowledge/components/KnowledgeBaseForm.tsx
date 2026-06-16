import { Form, Input, Select, type FormInstance } from "antd";

interface KnowledgeBaseFormProps {
	form: FormInstance;
	onFinish: (values: Record<string, unknown>) => void;
}

export function KnowledgeBaseForm({
	form,
	onFinish,
}: KnowledgeBaseFormProps) {
	return (
		<Form
			form={form}
			layout="vertical"
			initialValues={{
				name: "",
				description: "",
				status: "ACTIVE",
			}}
			onFinish={onFinish}
		>
			<Form.Item
				label="名称"
				name="name"
				rules={[{ required: true, message: "请输入知识库名称" }]}
			>
				<Input
					placeholder="例如：产品文档库"
					maxLength={100}
					showCount
				/>
			</Form.Item>
			<Form.Item label="描述" name="description">
				<Input.TextArea
					rows={3}
					maxLength={500}
					showCount
					placeholder="简要描述知识库的用途和覆盖范围"
				/>
			</Form.Item>
			<Form.Item label="状态" name="status">
				<Select
					options={[
						{ label: "ACTIVE (激活)", value: "ACTIVE" },
						{ label: "INACTIVE (停用)", value: "INACTIVE" },
					]}
				/>
			</Form.Item>
		</Form>
	);
}
