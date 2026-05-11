import { Tabs } from "antd";
import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import { MembersPage } from "./MembersPage";
import { AccountAdminPage } from "./AccountAdminPage";
import { AuditEventsPage } from "./AuditEventsPage";

const DEFAULT_TAB = "members";
const VALID_TABS = new Set(["members", "accounts", "audit"]);

export function AdminHomePage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const activeTab = useMemo(() => {
		const tab = searchParams.get("tab") ?? DEFAULT_TAB;
		return VALID_TABS.has(tab) ? tab : DEFAULT_TAB;
	}, [searchParams]);

	return (
		<Tabs
			activeKey={activeTab}
			onChange={(key) => setSearchParams({ tab: key })}
			items={[
				{
					key: "members",
					label: "成员管理",
					children: <MembersPage />,
				},
				{
					key: "accounts",
					label: "账号管理",
					children: <AccountAdminPage />,
				},
				{
					key: "audit",
					label: "审计日志",
					children: <AuditEventsPage />,
				},
			]}
		/>
	);
}
