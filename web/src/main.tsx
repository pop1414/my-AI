import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { App as AntdApp, ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import "antd/dist/reset.css";
import { AuthProvider } from "./shared/auth/AuthContext";
import { AppRoutes } from "./app/AppRoutes";
import "./index.css";

const queryClient = new QueryClient({
	defaultOptions: {
		queries: {
			retry: 1,
			refetchOnWindowFocus: false,
		},
	},
});

createRoot(document.getElementById("root")!).render(
	<StrictMode>
		<ConfigProvider
			locale={zhCN}
			theme={{
				token: {
					// Brand & Accent (DESIGN.md)
					colorPrimary: "#3ecf8e", // Emerald
					colorInfo: "#3ecf8e",
					colorSuccess: "#1f8a65",
					colorError: "#cf2d56",
					
					// Text
					colorText: "#171717", // Ink
					colorTextSecondary: "#212121", // Ink Secondary
					colorTextTertiary: "#707070", // Ink Mute
					
					// Surface & Borders
					colorBgLayout: "#ffffff", // Canvas
					colorBgContainer: "#ffffff", // Canvas
					colorBorder: "#e6e5e0", // Hairline
					colorBorderSecondary: "#e6e5e0",
					
					// Shapes & Elevation (DESIGN.md: No shadows, 6px radius)
					borderRadius: 6, // rounded.sm
					boxShadow: "none",
					boxShadowSecondary: "none",
					boxShadowTertiary: "none",
					
					// Typography
					fontFamily: "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif",
					fontFamilyCode: "'JetBrains Mono', 'Fira Code', monospace",
				},
				components: {
					Button: {
						fontWeight: 500,
						contentFontSize: 14,
						// Emerald button uses near-black text per DESIGN.md
						colorPrimaryText: "#171717",
						colorTextLightSolid: "#171717",
						primaryShadow: "none",
						borderRadius: 6,
					},
					Card: {
						borderRadiusLG: 12, // rounded.lg
						paddingLG: 32,
						boxShadowTertiary: "none",
					},
					Table: {
						borderRadius: 0,
						headerBg: "#fafafa",
						headerColor: "#707070",
						headerSplitColor: "transparent",
						borderColor: "#e6e5e0",
					},
					Menu: {
						itemBorderRadius: 6,
						subMenuItemBorderRadius: 6,
						itemSelectedColor: "#171717",
						itemSelectedBg: "#3ecf8e",
						activeBarHeight: 0,
						activeBarWidth: 0,
					},
					Typography: {
						fontWeightStrong: 500,
					},
					Breadcrumb: {
						itemColor: "#707070",
						lastItemColor: "#171717",
						separatorColor: "#e6e5e0",
						fontSize: 12,
					}
				},
			}}
		>
			<AntdApp>
				<QueryClientProvider client={queryClient}>
					<BrowserRouter>
						<AuthProvider>
							<AppRoutes />
						</AuthProvider>
					</BrowserRouter>
				</QueryClientProvider>
			</AntdApp>
		</ConfigProvider>
	</StrictMode>,
);
