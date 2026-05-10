import { Navigate, Outlet, useLocation } from "react-router-dom";
import { Button, Result, Spin } from "antd";
import { useAuth } from "./AuthContext";

// ── ProtectedRoute：要求已登录 ──

export function ProtectedRoute() {
	const { status } = useAuth();
	const location = useLocation();

	if (status === "loading") {
		return (
			<div
				style={{
					display: "flex",
					justifyContent: "center",
					alignItems: "center",
					height: "100vh",
				}}
			>
				<Spin size="large" tip="正在验证登录状态…" />
			</div>
		);
	}

	if (status === "anonymous") {
		const redirect = location.pathname + location.search;
		return (
			<Navigate
				to={`/login?redirect=${encodeURIComponent(redirect)}`}
				replace
			/>
		);
	}

	return <Outlet />;
}

// ── AdminRoute：要求已登录且为管理员 ──

export function AdminRoute() {
	const { isAuthenticated, isAdmin, status } = useAuth();

	if (status === "loading") {
		return (
			<div
				style={{
					display: "flex",
					justifyContent: "center",
					alignItems: "center",
					height: "100vh",
				}}
			>
				<Spin size="large" tip="正在验证登录状态…" />
			</div>
		);
	}

	if (!isAuthenticated) {
		const redirect = window.location.pathname + window.location.search;
		return (
			<Navigate
				to={`/login?redirect=${encodeURIComponent(redirect)}`}
				replace
			/>
		);
	}

	if (!isAdmin) {
		return (
			<Result
				status="403"
				title="403"
				subTitle="抱歉，您没有权限访问此页面。"
				extra={
					<Button
						type="primary"
						onClick={() => window.history.back()}
					>
						返回上一页
					</Button>
				}
			/>
		);
	}

	return <Outlet />;
}
