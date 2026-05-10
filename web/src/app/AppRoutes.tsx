import { lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { ConsoleLayout } from "./ConsoleLayout";
import { ProtectedRoute } from "../shared/auth/RouteGuards";

const LoginPage = lazy(() =>
	import("../features/auth/pages/LoginPage").then((m) => ({
		default: m.LoginPage,
	})),
);

const IngestListPage = lazy(() =>
	import("../features/ingest/pages/IngestListPage").then((m) => ({
		default: m.IngestListPage,
	})),
);
const IngestUploadPage = lazy(() =>
	import("../features/ingest/pages/IngestUploadPage").then((m) => ({
		default: m.IngestUploadPage,
	})),
);
const IngestStatusPage = lazy(() =>
	import("../features/ingest/pages/IngestStatusPage").then((m) => ({
		default: m.IngestStatusPage,
	})),
);
const IngestChunksPreviewPage = lazy(() =>
	import("../features/ingest/pages/IngestChunksPreviewPage").then((m) => ({
		default: m.IngestChunksPreviewPage,
	})),
);
const IngestReprocessPage = lazy(() =>
	import("../features/ingest/pages/IngestReprocessPage").then((m) => ({
		default: m.IngestReprocessPage,
	})),
);
const IngestDeletePage = lazy(() =>
	import("../features/ingest/pages/IngestDeletePage").then((m) => ({
		default: m.IngestDeletePage,
	})),
);
const KnowledgePage = lazy(() =>
	import("../features/knowledge/pages/KnowledgePage").then((m) => ({
		default: m.KnowledgePage,
	})),
);
const QaPage = lazy(() =>
	import("../features/qa/pages/QaPage").then((m) => ({ default: m.QaPage })),
);

export function AppRoutes() {
	return (
		<Routes>
			{/* 登录页：独立全屏布局，不使用 ConsoleLayout */}
			<Route path="/login" element={<LoginPage />} />

			{/* 以下路由均需登录 */}
			<Route element={<ProtectedRoute />}>
				<Route path="/" element={<ConsoleLayout />}>
					<Route
						index
						element={<Navigate to="/ingest/documents" replace />}
					/>

					{/* 文档列表主入口 */}
					<Route
						path="ingest/documents"
						element={<IngestListPage />}
					/>

					{/* 带 documentId 的嵌套跳转（从列表页进入） */}
					<Route
						path="ingest/documents/:documentId/status"
						element={<IngestStatusPage />}
					/>
					<Route
						path="ingest/documents/:documentId/chunks-preview"
						element={<IngestChunksPreviewPage />}
					/>
					<Route
						path="ingest/documents/:documentId/reprocess"
						element={<IngestReprocessPage />}
					/>
					<Route
						path="ingest/documents/:documentId/delete"
						element={<IngestDeletePage />}
					/>

					{/* 兼容旧路由：无 documentId 的独立访问入口 */}
					<Route
						path="ingest/upload"
						element={<IngestUploadPage />}
					/>
					<Route
						path="ingest/status"
						element={<IngestStatusPage />}
					/>
					<Route
						path="ingest/chunks-preview"
						element={<IngestChunksPreviewPage />}
					/>
					<Route
						path="ingest/reprocess"
						element={<IngestReprocessPage />}
					/>
					<Route
						path="ingest/delete"
						element={<IngestDeletePage />}
					/>

					{/* 旧版重定向 */}
					<Route
						path="ingest/list"
						element={<Navigate to="/ingest/documents" replace />}
					/>
					<Route
						path="reprocess"
						element={<Navigate to="/ingest/reprocess" replace />}
					/>
					<Route
						path="delete"
						element={<Navigate to="/ingest/delete" replace />}
					/>

					<Route path="knowledge" element={<KnowledgePage />} />
					<Route path="qa" element={<QaPage />} />
					<Route
						path="*"
						element={<Navigate to="/ingest/documents" replace />}
					/>
				</Route>
			</Route>
		</Routes>
	);
}
