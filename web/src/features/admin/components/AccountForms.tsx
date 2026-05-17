import { Form, Input, type FormInstance } from "antd";

interface AdminAccountFormProps {
	form: FormInstance;
	onFinish: (values: any) => void;
}

export function AdminAccountForm({ form, onFinish }: AdminAccountFormProps) {
	return (
		<Form form={form} layout="vertical" onFinish={onFinish}>
			<Form.Item
				name="username"
				label="用户名"
				rules={[{ required: true, message: "请输入用户名" }]}
			>
				<Input placeholder="登录使用的唯一账号名" />
			</Form.Item>
			<Form.Item
				name="displayName"
				label="显示名"
				rules={[{ required: true, message: "请输入显示名" }]}
			>
				<Input placeholder="对外展示的名称" />
			</Form.Item>
			<Form.Item
				name="password"
				label="初始密码"
				rules={[{ required: true, message: "请输入初始密码" }]}
			>
				<Input.Password placeholder="至少 8 位包含字母和数字" />
			</Form.Item>
			<Form.Item label="工作区角色">
				<Input value="WORKSPACE_ADMIN" disabled />
			</Form.Item>
		</Form>
	);
}

interface PasswordResetFormProps {
	form: FormInstance;
	onFinish: (values: any) => void;
	targetName: string;
}

export function PasswordResetForm({
	form,
	onFinish,
	targetName,
}: PasswordResetFormProps) {
	return (
		<Form form={form} layout="vertical" onFinish={onFinish}>
			<Form.Item label="目标账号">
				<span style={{ fontWeight: 600 }}>{targetName}</span>
			</Form.Item>
			<Form.Item
				name="password"
				label="新密码"
				rules={[{ required: true, message: "请输入新密码" }]}
			>
				<Input.Password placeholder="设置新的登录密码" />
			</Form.Item>
		</Form>
	);
}
