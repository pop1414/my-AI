import { Suspense, useEffect, useState, useCallback, useRef, useMemo } from "react";
import {
	AppstoreOutlined,
	CloudSyncOutlined,
	DatabaseOutlined,
	DownOutlined,
	FileTextOutlined,
	LogoutOutlined,
	MessageOutlined,
	MenuFoldOutlined,
	MenuUnfoldOutlined,
	SearchOutlined,
	SettingOutlined,
	TeamOutlined,
	UploadOutlined,
	UserOutlined,
} from "@ant-design/icons";
import {
	Badge,
	Breadcrumb,
	Button,
	Dropdown,
	Menu,
	Space,
	Spin,
	Tag,
	Tooltip,
	Typography,
} from "antd";
import type { MenuProps } from "antd";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../shared/auth/AuthContext";
import { WorkspaceRoleTag } from "../shared/ui/WorkspaceRoleTag";
import { useQuery } from "@tanstack/react-query";
import { listDocuments } from "../shared/api/ingestApi";

const { Title, Text } = Typography;

type MenuItem = Required<MenuProps>["items"][number];

const MODULE_OPEN_KEYS = [
	"module:documents",
	"module:knowledge",
	"module:qa",
	"module:admin",
];

const MIN_SIDEBAR_WIDTH = 200;
const MAX_SIDEBAR_WIDTH = 480;
const DEFAULT_SIDEBAR_WIDTH = 260;

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
	const navigate = useNavigate();
	const { user, visibleMenuKeys, logout } = useAuth();
	
	// Activity Center State
	const activityQuery = useQuery({
		queryKey: ["admin", "activity-tasks"],
		queryFn: () => listDocuments({ limit: 10 }), // In real app, filter by processing statuses
		refetchInterval: 10000, // Poll every 10s
		enabled: !!user,
	});

	const processingTasks = useMemo(() => {
		const items = activityQuery.data?.items ?? [];
		return items.filter(item => ["PROCESSING", "INDEXING", "UPLOADED", "PENDING"].includes(item.status));
	}, [activityQuery.data]);

	// Resizable Sidebar State
	const [sidebarWidth, setSidebarWidth] = useState(() => {
		const saved = localStorage.getItem("console-sidebar-width");
		return saved ? parseInt(saved, 10) : DEFAULT_SIDEBAR_WIDTH;
	});
	const [isCollapsed, setIsCollapsed] = useState(() => {
		return localStorage.getItem("console-sidebar-collapsed") === "true";
	});
	const [isDragging, setIsDragging] = useState(false);
	const dragRef = useRef<boolean>(false);

	const handleMouseDown = useCallback(() => {
		if (isCollapsed) return;
		setIsDragging(true);
		dragRef.current = true;
		document.body.style.cursor = "col-resize";
		document.body.style.userSelect = "none";
	}, [isCollapsed]);

	useEffect(() => {
		const handleMouseMove = (e: MouseEvent) => {
			if (!dragRef.current) return;
			const newWidth = Math.min(Math.max(e.clientX, MIN_SIDEBAR_WIDTH), MAX_SIDEBAR_WIDTH);
			setSidebarWidth(newWidth);
		};

		const handleMouseUp = () => {
			if (!dragRef.current) return;
			setIsDragging(false);
			dragRef.current = false;
			document.body.style.cursor = "";
			document.body.style.userSelect = "";
			localStorage.setItem("console-sidebar-width", sidebarWidth.toString());
		};

		if (isDragging) {
			window.addEventListener("mousemove", handleMouseMove);
			window.addEventListener("mouseup", handleMouseUp);
		}

		return () => {
			window.removeEventListener("mousemove", handleMouseMove);
			window.removeEventListener("mouseup", handleMouseUp);
		};
	}, [isDragging, sidebarWidth]);

	const toggleCollapse = () => {
		const nextState = !isCollapsed;
		setIsCollapsed(nextState);
		localStorage.setItem("console-sidebar-collapsed", String(nextState));
	};

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

	const activityMenuItems: MenuProps["items"] = useMemo(() => {
		if (processingTasks.length === 0) {
			return [{
				key: "empty",
				label: <div style={{ padding: '8px 4px', textAlign: 'center', color: 'var(--console-ink-mute)' }}>暂无运行中的任务</div>,
				disabled: true,
			}];
		}

		return processingTasks.map(task => ({
			key: task.documentId,
			label: (
				<div style={{ padding: '4px 0', minWidth: 200 }}>
					<div style={{ fontWeight: 500, marginBottom: 2 }}>{task.filename}</div>
					<div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
						<Tag bordered={false} color="processing" style={{ fontSize: 11, margin: 0 }}>{task.status}</Tag>
						<Text type="secondary" style={{ fontSize: 11, fontFamily: 'var(--console-font-mono)' }}>{task.documentId.slice(0, 8)}...</Text>
					</div>
				</div>
			),
			onClick: () => navigate(`/ingest/documents/${task.documentId}`)
		}));
	}, [processingTasks, navigate]);

	return (
		<div className="console-shell" data-testid="console-layout">
			<a className="console-skip-link" href="#console-main">
				跳到主内容
			</a>
			
			{showSidebar && (
				<aside 
					className="console-aside" 
					style={{ 
						width: isCollapsed ? 0 : sidebarWidth,
						transition: isDragging ? 'none' : 'width 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
						overflow: isCollapsed ? 'hidden' : 'visible'
					}}
				>
					<div className="console-logo">
						my-AI / Web Console
					</div>
					<nav className="console-sidebar-menu-wrapper" aria-label="控制台主导航">
						<Menu
							mode="inline"
							selectedKeys={[
								resolveMenuSelectedKey(location.pathname, location.search),
							]}
							defaultOpenKeys={MODULE_OPEN_KEYS}
							items={menuItems}
						/>
					</nav>
					{!isCollapsed && (
						<div 
							className={`console-aside-resizer ${isDragging ? "is-dragging" : ""}`}
							onMouseDown={handleMouseDown}
						/>
					)}
				</aside>
			)}

			<div className="console-main-container">
				<header className="console-header">
					<Space size={16}>
						{showSidebar && (
							<Tooltip title={isCollapsed ? "展开侧边栏" : "收起侧边栏"} placement="right">
								<Button
									type="text"
									icon={isCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
									onClick={toggleCollapse}
									className="console-collapse-btn"
									style={{ fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
								/>
							</Tooltip>
						)}
						<div className="console-header-copy">
							<Title level={4} className="console-page-title" data-testid="console-title" style={{ margin: 0 }}>
								{routeMeta.pageTitle}
							</Title>
							<Breadcrumb
								items={[
									{ title: "控制台" }, 
									{ title: routeMeta.moduleLabel }, 
									{ title: routeMeta.pageTitle }
								]}
							/>
						</div>
					</Space>
					<div className="console-header-actions">
						<Space size={20}>
							<Dropdown menu={{ items: activityMenuItems }} placement="bottomRight" trigger={['click']}>
								<Badge count={processingTasks.length} size="small" offset={[-2, 2]}>
									<Button 
										type="text" 
										icon={<CloudSyncOutlined style={{ fontSize: 18, color: processingTasks.length > 0 ? 'var(--console-accent)' : 'inherit' }} />} 
										className={processingTasks.length > 0 ? "console-activity-btn is-active" : "console-activity-btn"}
									/>
								</Badge>
							</Dropdown>

							<Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
								<Space style={{ cursor: "pointer" }}>
									<UserOutlined />
									<span style={{ fontWeight: 500 }}>{user?.displayName ?? ""}</span>
									{user?.workspaceRole && (
										<WorkspaceRoleTag role={user.workspaceRole} />
									)}
									<DownOutlined style={{ fontSize: 10 }} />
								</Space>
							</Dropdown>
						</Space>
					</div>
				</header>
				
				<main id="console-main" className="console-content">
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
			</div>
		</div>
	);
}
