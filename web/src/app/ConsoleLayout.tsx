import { Suspense } from "react";
import {
	AppstoreOutlined,
	DatabaseOutlined,
	DownOutlined,
	FileTextOutlined,
	LogoutOutlined,
	MessageOutlined,
	SearchOutlined,
	SettingOutlined,
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

const MODULE_OPEN_KEYS = [
	"module:documents",
	"module:knowledge",
	"module:qa",
	"module:admin",
];

function createModuleLabel(testId: string, label: string) {
	return <span data-testid={testId}>{label}</span>;
}

function buildMenuItems(visibleMenuKeys: string[]): MenuItem[] {
	const items: MenuItem[] = [];
	const canAccessDocuments =
		visibleMenuKeys.includes("/ingest/documents") ||
		visibleMenuKeys.includes("/ingest/upload");

	if (canAccessDocuments) {
		const documentChildren: NonNullable<MenuItem>[] = [];
		if (visibleMenuKeys.includes("/ingest/documents")) {
			documentChildren.push({
				key: "/ingest/documents",
				icon: <FileTextOutlined />,
				label: "文档目录",
			});
		}
		if (visibleMenuKeys.includes("/ingest/upload")) {
			documentChildren.push({
				key: "/ingest/upload",
				icon: <UploadOutlined />,
				label: "文档接入",
			});
		}
		items.push({
			key: "module:documents",
			icon: <AppstoreOutlined />,
			label: createModuleLabel("console-module-documents", "文档"),
			children: documentChildren,
		});
	}

	if (visibleMenuKeys.includes("/knowledge")) {
		items.push({
			key: "module:knowledge",
			icon: <DatabaseOutlined />,
			label: createModuleLabel("console-module-knowledge", "知识库"),
			children: [
				{
					key: "/knowledge",
					icon: <DatabaseOutlined />,
					label: "知识库总览",
				},
			],
		});
	}
	if (visibleMenuKeys.includes("/qa")) {
		items.push({
			key: "module:qa",
			icon: <MessageOutlined />,
			label: createModuleLabel("console-module-qa", "问答"),
			children: [
				{
					key: "/qa",
					icon: <SearchOutlined />,
					label: "问答工作台",
				},
			],
		});
	}
	if (visibleMenuKeys.includes("/admin")) {
		items.push({
			key: "module:admin",
			icon: <SettingOutlined />,
			label: createModuleLabel("console-module-admin", "系统管理"),
			children: [
				{
					key: "/admin?tab=members",
					icon: <TeamOutlined />,
					label: "成员与权限",
				},
				{
					key: "/admin?tab=accounts",
					icon: <UserOutlined />,
					label: "账号管理",
				},
				{
					key: "/admin?tab=audit",
					icon: <FileTextOutlined />,
					label: "审计日志",
				},
			],
		});
	}

	return items;
}

function resolveRouteMeta(pathname: string, search: string) {
	if (pathname.includes("/prototype-read")) {
		return {
			moduleLabel: "文档",
			pageTitle: "版本内容阅读原型",
		};
	}
	if (pathname.includes("/versions/") && pathname.endsWith("/read")) {
		return {
			moduleLabel: "文档",
			pageTitle: "版本内容阅读",
		};
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
		return {
			moduleLabel: "文档",
			pageTitle: map[action] ?? "文档详情",
		};
	}
	if (pathname.startsWith("/admin/knowledge-bases/")) {
		return {
			moduleLabel: "系统管理",
			pageTitle: "知识库授权管理",
		};
	}
	if (pathname.startsWith("/admin/documents/")) {
		return {
			moduleLabel: "系统管理",
			pageTitle: "文档授权管理",
		};
	}
	if (pathname.startsWith("/admin/members/") && pathname.endsWith("/grants")) {
		return {
			moduleLabel: "系统管理",
			pageTitle: "成员授权配置",
		};
	}
	if (pathname === "/admin") {
		const tab = new URLSearchParams(search).get("tab") ?? "members";
		const tabTitleMap: Record<string, string> = {
			members: "成员管理",
			accounts: "账号管理",
			audit: "审计日志",
		};
		return {
			moduleLabel: "系统管理",
			pageTitle: tabTitleMap[tab] ?? "成员管理",
		};
	}
	if (pathname === "/no-access") {
		return {
			moduleLabel: "控制台",
			pageTitle: "访问受限",
		};
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
	const moduleMap: Record<string, string> = {
		"/ingest/documents": "文档",
		"/ingest/upload": "文档",
		"/knowledge": "知识库",
		"/qa": "问答",
	};
	return {
		moduleLabel: moduleMap[pathname] ?? "控制台",
		pageTitle: map[pathname] ?? "Ingest 控制台",
	};
}

function resolveMenuSelectedKey(pathname: string, search: string): string {
	if (pathname.startsWith("/ingest/documents/")) {
		return "/ingest/documents";
	}
	if (
		pathname.startsWith("/admin/documents/") ||
		pathname.startsWith("/admin/knowledge-bases/") ||
		pathname.startsWith("/admin/members/")
	) {
		return "module:admin";
	}
	if (pathname === "/admin") {
		const tab = new URLSearchParams(search).get("tab") ?? "members";
		return `/admin?tab=${tab}`;
	}
	return pathname;
}

export function ConsoleLayout() {
	const navigate = useNavigate();
	const location = useLocation();
	const { user, visibleMenuKeys, logout } = useAuth();

	const menuItems = buildMenuItems(visibleMenuKeys);
	const showSidebar = menuItems.length > 0;
	const routeMeta = resolveRouteMeta(location.pathname, location.search);

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
				items={[{ title: "控制台" }, { title: routeMeta.moduleLabel }, { title: routeMeta.pageTitle }]}
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
				<Sider width={268} breakpoint="lg" collapsedWidth="0" className="console-sidebar">
					<div className="console-logo">my-AI / Web Console</div>
					<Menu
						className="console-sidebar-menu"
						mode="inline"
						selectedKeys={[
							resolveMenuSelectedKey(location.pathname, location.search),
						]}
						defaultOpenKeys={MODULE_OPEN_KEYS}
						items={menuItems}
						onClick={({ key }) => navigate(String(key))}
					/>
				</Sider>
			)}
			<Layout>
				<Header className="console-header">
					<div className="console-header-copy">
						<Space size={[8, 8]} wrap>
							<Tag color="blue">{routeMeta.moduleLabel}</Tag>
							<Text type="secondary">外层模块导航 + 统一页面骨架</Text>
						</Space>
						<Title level={4} style={{ margin: 0 }} data-testid="console-title">
							my-AI 控制台
						</Title>
						<Text type="secondary">
							以模块稳定分区承接文档、知识库、问答与治理任务，页面内统一为摘要、工作区和状态区。
						</Text>
					</div>
					<Space className="console-header-actions" size={12}>
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
