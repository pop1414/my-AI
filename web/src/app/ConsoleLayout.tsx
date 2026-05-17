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
import { Link, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../shared/auth/AuthContext";

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

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

function createMenuLinkLabel(to: string, label: string) {
	return (
		<Link className="console-menu-link" to={to}>
			{label}
		</Link>
	);
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
				label: createMenuLinkLabel("/ingest/documents", "文档目录"),
			});
		}
		if (visibleMenuKeys.includes("/ingest/upload")) {
			documentChildren.push({
				key: "/ingest/upload",
				icon: <UploadOutlined />,
				label: createMenuLinkLabel("/ingest/upload", "文档接入"),
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
					label: createMenuLinkLabel("/knowledge", "知识库总览"),
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
					label: createMenuLinkLabel("/qa", "问答工作台"),
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
					label: createMenuLinkLabel("/admin?tab=members", "成员与权限"),
				},
				{
					key: "/admin?tab=accounts",
					icon: <UserOutlined />,
					label: createMenuLinkLabel("/admin?tab=accounts", "账号管理"),
				},
				{
					key: "/admin?tab=audit",
					icon: <FileTextOutlined />,
					label: createMenuLinkLabel("/admin?tab=audit", "审计日志"),
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
	const location = useLocation();
	const { user, visibleMenuKeys, logout } = useAuth();
	const isQaLayout = location.pathname === "/qa";

	const menuItems = buildMenuItems(visibleMenuKeys);
	const showSidebar = menuItems.length > 0;
	const routeMeta = resolveRouteMeta(location.pathname, location.search);

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
		<>
			<a className="console-skip-link" href="#console-main">
				跳到主内容
			</a>
			<Layout
				className={isQaLayout ? "console-root console-root--qa" : "console-root"}
				data-testid="console-layout"
			>
				{showSidebar && (
					<Sider
						width={isQaLayout ? 92 : 268}
						breakpoint="lg"
						collapsed={isQaLayout}
						collapsedWidth={isQaLayout ? 92 : 0}
						className={isQaLayout ? "console-sidebar console-sidebar--qa" : "console-sidebar"}
					>
						<div className={isQaLayout ? "console-logo console-logo--qa" : "console-logo"}>
							{isQaLayout ? "AI" : "my-AI / Web Console"}
						</div>
						<nav aria-label="控制台主导航">
							<Menu
								className="console-sidebar-menu"
								mode="inline"
								inlineCollapsed={isQaLayout}
								selectedKeys={[
									resolveMenuSelectedKey(location.pathname, location.search),
								]}
								defaultOpenKeys={MODULE_OPEN_KEYS}
								items={menuItems}
							/>
						</nav>
					</Sider>
				)}
				<Layout>
					<Header className="console-header">
						<div className="console-header-copy">
							<Title level={4} style={{ margin: 0, fontWeight: 500, letterSpacing: '-0.02em' }} data-testid="console-title">
								{routeMeta.pageTitle}
							</Title>
							<Breadcrumb
								items={[{ title: "控制台" }, { title: routeMeta.moduleLabel }, { title: routeMeta.pageTitle }]}
								style={{ fontSize: 12 }}
							/>
						</div>
						<Space className="console-header-actions" size={16}>
							<Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
								<Space style={{ cursor: "pointer" }}>
									<UserOutlined />
									<span style={{ fontWeight: 500 }}>{user?.displayName ?? ""}</span>
									{user?.workspaceRole && (
										<Tag
											bordered={false}
											color="default"
											style={{ fontSize: 11, textTransform: 'uppercase' }}
										>
											{user.workspaceRole}
										</Tag>
									)}
									<DownOutlined style={{ fontSize: 10 }} />
								</Space>
							</Dropdown>
						</Space>
					</Header>
					<Content className="console-content">
						<main id="console-main" className="console-main">
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
						</main>
					</Content>

				</Layout>
			</Layout>
		</>
	);
}
