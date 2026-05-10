import { useMemo } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { Alert, Button, Card, Form, Input, Typography, theme } from "antd";
import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { useMutation } from "@tanstack/react-query";
import { useAuth } from "../../../shared/auth/AuthContext";
import type { ApiError } from "../../../shared/api/request";

const { Title, Text } = Typography;

function formatLockedUntilMessage(rawMessage: string): string | null {
	const prefix = "account is locked until ";
	if (!rawMessage.startsWith(prefix)) {
		return null;
	}
	const iso = rawMessage.slice(prefix.length).trim();
	const lockedUntil = new Date(iso);
	if (Number.isNaN(lockedUntil.getTime())) {
		return "账号已锁定，请稍后再试。";
	}
	return `账号已锁定，请于 ${lockedUntil.toLocaleString("zh-CN", {
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
		second: "2-digit",
		hour12: false,
	})} 后重试。`;
}

export function LoginPage() {
	const { isAuthenticated, login } = useAuth();
	const [searchParams] = useSearchParams();
	const { token } = theme.useToken();

	const redirect = useMemo(() => {
		const fromParam = searchParams.get("redirect");
		return fromParam || "/ingest/documents";
	}, [searchParams]);

	const mutation = useMutation({
		mutationFn: (values: { username: string; password: string }) =>
			login(values.username, values.password),
		onSuccess: () => {
			window.location.href = redirect;
		},
	});

	// 已登录用户直接跳转
	if (isAuthenticated) {
		return <Navigate to={redirect} replace />;
	}

	const apiError = mutation.error as ApiError | null;
	const errorMessage = useMemo(() => {
		if (!apiError) {
			return null;
		}
		if (apiError.status === 401) {
			return "用户名或密码错误，请重新输入。";
		}
		if (apiError.status === 403) {
			if (apiError.message === "account is disabled") {
				return "账号已被禁用，请联系管理员。";
			}
			return (
				formatLockedUntilMessage(apiError.message) ??
				"账号已锁定，请稍后再试。"
			);
		}
		return `登录失败：${apiError.message}`;
	}, [apiError]);

	return (
		<div
			style={{
				display: "flex",
				justifyContent: "center",
				alignItems: "center",
				minHeight: "100vh",
				background: token.colorBgLayout,
			}}
		>
			<Card
				style={{
					width: 400,
					boxShadow: token.boxShadow,
				}}
				styles={{ body: { padding: 32 } }}
			>
				<div style={{ textAlign: "center", marginBottom: 32 }}>
					<Title level={2} style={{ marginBottom: 4 }}>
						my-AI
					</Title>
					<Text type="secondary">V1 · 智能文档问答平台</Text>
				</div>

				<Form
					name="login"
					size="large"
					onFinish={(values) => mutation.mutate(values)}
					autoComplete="off"
				>
					<Form.Item
						name="username"
						rules={[{ required: true, message: "请输入用户名" }]}
					>
						<Input
							prefix={<UserOutlined />}
							placeholder="用户名"
							autoFocus
						/>
					</Form.Item>

					<Form.Item
						name="password"
						rules={[{ required: true, message: "请输入密码" }]}
					>
						<Input.Password
							prefix={<LockOutlined />}
							placeholder="密码"
						/>
					</Form.Item>

					{errorMessage && (
						<Form.Item>
							<Alert type="error" showIcon message={errorMessage} />
						</Form.Item>
					)}

					<Form.Item style={{ marginBottom: 0 }}>
						<Button
							type="primary"
							htmlType="submit"
							loading={mutation.isPending}
							block
						>
							登录
						</Button>
					</Form.Item>
				</Form>
			</Card>
		</div>
	);
}
