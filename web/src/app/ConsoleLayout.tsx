import { Suspense } from "react";
import {
	DownOutlined,
	FileTextOutlined,
	LogoutOutlined,
	SearchOutlined,
	TeamOutlined,
	UploadOutlined,
	UserOutlined,
} from "@ant-design/icons";
import {
	Breadcrumb,
	Button,
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

function buildMenuItems(visibleMenuKeys: string[]): MenuItem[] {
	const items: MenuItem[] = [];

	if (visibleMenuKeys.includes("/ingest/documents")) {
		items.push({
			key: "/ingest/documents",
			icon: <FileTextOutlined />,
			label: "文档列表",
		});
	}
	if (visibleMenuKeys.includes("/ingest/upload")) {
		items.push({
			key: "/ingest/upload",
			icon: <UploadOutlined />,
			label: "文档上传",
		});
	}
	if (visibleMenuKeys.includes("/knowledge")) {
		items.push({
			key: "/knowledge",
			icon: <FileTextOutlined />,
			label: "知识库",
		});
	}
	if (visibleMenuKeys.includes("/qa")) {
		items.push({
			key: "/qa",
			icon: <SearchOutlined />,
			label: "问答",
		});
	}
	if (visibleMenuKeys.includes("/admin")) {
		if (items.length > 0) {
			items.push({ type: "divider" });
		}
		items.push({ key: "/admin", icon: <TeamOutlined />, label: "系统管理" });
	}

	return items;
}

function resolveTitle(pathname: string): string {
	if (pathname.includes("/prototype-read")) {
		return "版本内容阅读原型";
	}
	if (pathname.includes("/versions/") && pathname.endsWith("/read")) {
		return "版本内容阅读";
	}
	if (pathname.startsWith("/ingest/documents/")) {
		const suffix = pathname.split("/").slice(3).join("/");
		const map: Record<string, string> = {
			status: "文档状态查询",
			prototype: "文档详情原型",
			"chunks-preview": "文档分块预览",
			reprocess: "文档重处理",
			delete: "删除文档资产",
		};
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
	if (pathname.startsWith("/admin/members/") && pathname.endsWith("/grants")) {
		return "成员授权配置";
	}
	if (pathname === "/admin") {
		return "系统管理";
	}
	if (pathname === "/no-access") {
		return "访问受限";
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
	};
	return map[pathname] ?? "Ingest 控制台";
}

function resolveMenuSelectedKey(pathname: string): string {
	if (pathname.startsWith("/ingest/documents/")) {
		return "/ingest/documents";
	}
	if (pathname.startsWith("/admin/documents/")) {
		return "/ingest/documents";
	}
	if (
		pathname.startsWith("/admin/knowledge-bases/") ||
		pathname.startsWith("/admin")
	) {
		return "/admin";
	}
	return pathname;
}

export function ConsoleLayout() {
	const navigate = useNavigate();
	const location = useLocation();
	const { user, visibleMenuKeys, logout } = useAuth();

	const menuItems = buildMenuItems(visibleMenuKeys);
	const showSidebar = menuItems.length > 0;
	const canOpenQa = visibleMenuKeys.includes("/qa");
	const isQaPage = location.pathname === "/qa";

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

	const content = (
		<>
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
		</>
	);

	return (
		<Layout className="console-root" data-testid="console-layout">
			{showSidebar && (
				<Sider width={250} breakpoint="lg" collapsedWidth="0">
					<div className="console-logo">my-AI / Web Console</div>
					<Menu
						theme="dark"
						mode="inline"
						selectedKeys={[resolveMenuSelectedKey(location.pathname)]}
						items={menuItems}
						onClick={({ key }) => navigate(String(key))}
					/>
				</Sider>
			)}
			<Layout>
				<Header className="console-header">
					<div>
						<Title
							level={4}
							style={{ margin: 0 }}
							data-testid="console-title"
						>
							{resolveTitle(location.pathname)}
						</Title>
						<Text type="secondary">
							V1 闭环：文档上传 · 分块检索 · 知识库统计 · 单轮问答
						</Text>
					</div>
					<Space className="console-header-actions" size={12}>
						{canOpenQa && !isQaPage && (
							<Button
								type="primary"
								icon={<SearchOutlined />}
								onClick={() => navigate("/qa")}
								data-testid="header-qa-entry"
							>
								问答控制台
							</Button>
						)}
						<Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
							<Space style={{ cursor: "pointer" }}>
								<UserOutlined />
								<span>{user?.displayName ?? ""}</span>
								{user?.workspaceRole && (
									<Tag
										color={roleColorMap[user.workspaceRole] ?? "default"}
									>
										{user.workspaceRole}
									</Tag>
								)}
								<DownOutlined />
							</Space>
						</Dropdown>
					</Space>
				</Header>
				<Content className="console-content">{content}</Content>
			</Layout>
		</Layout>
	);
}
