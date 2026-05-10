import { Suspense } from "react";
import {
	AuditOutlined,
	DeleteOutlined,
	FileSearchOutlined,
	FileSyncOutlined,
	FileTextOutlined,
	LogoutOutlined,
	SearchOutlined,
	TeamOutlined,
	UploadOutlined,
	UserOutlined,
} from "@ant-design/icons";
import {
	Breadcrumb,
	Dropdown,
	Layout,
	Menu,
	Space,
	Spin,
	Tag,
	Typography,
} from "antd";
import type { MenuProps } from "antd";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../shared/auth/AuthContext";

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

type MenuItem = Required<MenuProps>["items"][number];

function buildMenuItems(isAdmin: boolean): MenuItem[] {
	const businessItems: MenuItem[] = [
		{
			key: "/ingest/documents",
			icon: <FileTextOutlined />,
			label: "文档列表",
		},
		{ key: "/ingest/upload", icon: <UploadOutlined />, label: "文档上传" },
		{
			key: "/ingest/status",
			icon: <FileSyncOutlined />,
			label: "状态查询",
		},
		{
			key: "/ingest/chunks-preview",
			icon: <FileSearchOutlined />,
			label: "分块预览",
		},
		{
			key: "/ingest/reprocess",
			icon: <FileSyncOutlined />,
			label: "重处理",
		},
		{ key: "/ingest/delete", icon: <DeleteOutlined />, label: "删除文档" },
		{ type: "divider" },
		{ key: "/knowledge", icon: <FileTextOutlined />, label: "知识库" },
		{ key: "/qa", icon: <SearchOutlined />, label: "问答" },
	];

	if (!isAdmin) return businessItems;

	const adminItems: MenuItem[] = [
		{ type: "divider" },
		{ type: "group", label: "系统管理" },
		{ key: "/admin/members", icon: <TeamOutlined />, label: "成员管理" },
		{
			key: "/admin/audit-events",
			icon: <AuditOutlined />,
			label: "审计日志",
		},
	];

	return [...businessItems, ...adminItems];
}

function resolveTitle(pathname: string): string {
	if (pathname.startsWith("/ingest/documents/")) {
		const suffix = pathname.split("/").slice(3).join("/");
		const map: Record<string, string> = {
			status: "文档状态查询",
			"chunks-preview": "文档分块预览",
			reprocess: "文档重处理",
			delete: "删除文档资产",
		};
		// 提取纯操作名（去掉 documentId 段）
		const action = suffix.includes("/")
			? suffix.split("/").slice(1).join("/")
			: suffix;
		return map[action] ?? "文档详情";
	}
	if (pathname.startsWith("/admin/knowledge-bases/")) {
		return "知识库授权管理";
	}
	if (pathname.startsWith("/admin/documents/")) {
		return "文档授权管理";
	}
	const map: Record<string, string> = {
		"/ingest/documents": "文档列表与管理台",
		"/ingest/upload": "文档上传受理",
		"/ingest/status": "文档状态查询",
		"/ingest/chunks-preview": "文档分块预览",
		"/ingest/reprocess": "文档重处理",
		"/ingest/delete": "删除文档资产",
		"/knowledge": "知识库管理",
		"/qa": "问答控制台",
		"/admin/members": "成员管理",
		"/admin/audit-events": "审计日志",
	};
	return map[pathname] ?? "Ingest 控制台";
}

function resolveMenuSelectedKey(pathname: string): string {
	// 所有 /ingest/documents/... 嵌套路由都高亮"文档列表"
	if (pathname.startsWith("/ingest/documents/")) {
		return "/ingest/documents";
	}
	// 知识库授权管理页高亮"知识库"
	if (pathname.startsWith("/admin/knowledge-bases/")) {
		return "/knowledge";
	}
	// 文档授权管理页高亮"文档列表"
	if (pathname.startsWith("/admin/documents/")) {
		return "/ingest/documents";
	}
	return pathname;
}

export function ConsoleLayout() {
	const navigate = useNavigate();
	const location = useLocation();
	const { user, isAdmin, logout } = useAuth();

	const menuItems = buildMenuItems(isAdmin);

	const roleColorMap: Record<string, string> = {
		WORKSPACE_OWNER: "gold",
		WORKSPACE_ADMIN: "blue",
		WORKSPACE_MEMBER: "default",
	};

	const userMenuItems: MenuProps["items"] = [
		{
			key: "info",
			label: user?.displayName ?? user?.username ?? "未登录",
			disabled: true,
		},
		{ type: "divider" },
		{
			key: "logout",
			icon: <LogoutOutlined />,
			label: "退出登录",
			onClick: () => logout(),
		},
	];

	return (
		<Layout className="console-root">
			<Sider width={250} breakpoint="lg" collapsedWidth="0">
				<div className="console-logo">my-AI / Web Console</div>
				<Menu
					theme="dark"
					mode="inline"
					selectedKeys={[resolveMenuSelectedKey(location.pathname)]}
					items={menuItems}
					onClick={({ key }) => navigate(key)}
				/>
			</Sider>
			<Layout>
				<Header className="console-header">
					<div>
						<Title level={4} style={{ margin: 0 }}>
							{resolveTitle(location.pathname)}
						</Title>
						<Text type="secondary">
							V1 闭环：文档上传 · 分块检索 · 知识库统计 · 单轮问答
						</Text>
					</div>
					<Dropdown
						menu={{ items: userMenuItems }}
						placement="bottomRight"
					>
						<Space style={{ cursor: "pointer" }}>
							<UserOutlined />
							<span>{user?.displayName ?? ""}</span>
							{user?.workspaceRole && (
								<Tag
									color={
										roleColorMap[user.workspaceRole] ??
										"default"
									}
								>
									{user.workspaceRole}
								</Tag>
							)}
						</Space>
					</Dropdown>
				</Header>
				<Content className="console-content">
					<Breadcrumb
						items={[
							{ title: "控制台" },
							{ title: resolveTitle(location.pathname) },
						]}
						style={{ marginBottom: 16 }}
					/>
					<Suspense
						fallback={
							<div
								style={{
									display: "flex",
									justifyContent: "center",
									alignItems: "center",
									minHeight: 300,
								}}
							>
								<Spin size="large" />
							</div>
						}
					>
						<Outlet />
					</Suspense>
				</Content>
			</Layout>
		</Layout>
	);
}
