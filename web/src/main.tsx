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
					colorPrimary: "#2563eb",
					colorInfo: "#2563eb",
					colorLink: "#2563eb",
					colorSuccess: "#3b82f6",
					borderRadius: 12,
					controlOutline: "rgba(37, 99, 235, 0.18)",
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
