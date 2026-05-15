import { lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { ConsoleLayout } from "./ConsoleLayout";
import {
	AdminRoute,
	CapabilityRoute,
	ProtectedRoute,
} from "../shared/auth/RouteGuards";
import { useAuth } from "../shared/auth/AuthContext";

const LoginPage = lazy(() =>
	import("../features/auth/pages/LoginPage").then((m) => ({
		default: m.LoginPage,
	})),
);

const AdminHomePage = lazy(() =>
	import("../features/admin/pages/AdminHomePage").then((m) => ({
		default: m.AdminHomePage,
	})),
);

const KnowledgeBaseGrantsPage = lazy(() =>
	import("../features/admin/pages/KnowledgeBaseGrantsPage").then((m) => ({
		default: m.KnowledgeBaseGrantsPage,
	})),
);

const DocumentGrantsPage = lazy(() =>
	import("../features/admin/pages/DocumentGrantsPage").then((m) => ({
		default: m.DocumentGrantsPage,
	})),
);
const MemberGrantsPage = lazy(() =>
	import("../features/admin/pages/MemberGrantsPage").then((m) => ({
		default: m.MemberGrantsPage,
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
const IngestDocumentDetailPage = lazy(() =>
	import("../features/ingest/pages/IngestDocumentDetailPage").then((m) => ({
		default: m.IngestDocumentDetailPage,
	})),
);
const IngestDocumentVersionReadPage = lazy(() =>
	import("../features/ingest/pages/IngestDocumentVersionReadPage").then((m) => ({
		default: m.IngestDocumentVersionReadPage,
	})),
);
const IngestStatusPage = lazy(() =>
	import("../features/ingest/pages/IngestStatusPage").then((m) => ({
		default: m.IngestStatusPage,
	})),
);
const IngestDocumentDetailPrototypePage = lazy(() =>
	import("../features/ingest/pages/IngestDocumentDetailPrototypePage").then(
		(m) => ({
			default: m.IngestDocumentDetailPrototypePage,
		}),
	),
);
const IngestDocumentVersionReadPrototypePage = lazy(() =>
	import("../features/ingest/pages/IngestDocumentVersionReadPrototypePage").then(
		(m) => ({
			default: m.IngestDocumentVersionReadPrototypePage,
		}),
	),
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
const PlaceholderPage = lazy(() =>
	import("../features/placeholder/pages/PlaceholderPage").then((m) => ({
		default: m.PlaceholderPage,
	})),
);

function HomeRedirect() {
	const { defaultLandingPath } = useAuth();
	return <Navigate to={defaultLandingPath} replace />;
}

export function AppRoutes() {
	return (
		<Routes>
			<Route path="/login" element={<LoginPage />} />

			<Route element={<ProtectedRoute />}>
				<Route path="/" element={<ConsoleLayout />}>
					<Route index element={<HomeRedirect />} />

					<Route path="no-access" element={<PlaceholderPage title="暂无可访问功能" description="当前账号暂无可访问功能，请联系管理员分配权限。" />} />

					<Route
						element={
							<CapabilityRoute requiredCapability="canAccessDocumentList" />
						}
					>
						<Route path="ingest/documents" element={<IngestListPage />} />
					</Route>

					<Route
						element={<CapabilityRoute requiredCapability="canUploadDocument" />}
					>
						<Route path="ingest/upload" element={<IngestUploadPage />} />
					</Route>

					<Route
						element={<CapabilityRoute requiredCapability="canAccessKnowledge" />}
					>
						<Route path="knowledge" element={<KnowledgePage />} />
					</Route>

					<Route element={<CapabilityRoute requiredCapability="canAskQuestion" />}>
						<Route path="qa" element={<QaPage />} />
					</Route>

					<Route
						path="ingest/documents/:documentId/status"
						element={<IngestStatusPage />}
					/>
					<Route
						path="ingest/documents/:documentId"
						element={<IngestDocumentDetailPage />}
					/>
					<Route
						path="ingest/documents/:documentId/versions/:versionNumber/read"
						element={<IngestDocumentVersionReadPage />}
					/>
					<Route
						path="ingest/documents/:documentId/prototype"
						element={<IngestDocumentDetailPrototypePage />}
					/>
					<Route
						path="ingest/documents/:documentId/versions/:versionNumber/prototype-read"
						element={<IngestDocumentVersionReadPrototypePage />}
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

					<Route
						path="ingest/status"
						element={<Navigate to="/ingest/documents" replace />}
					/>
					<Route
						path="ingest/chunks-preview"
						element={<Navigate to="/ingest/documents" replace />}
					/>
					<Route
						path="ingest/reprocess"
						element={<Navigate to="/ingest/documents" replace />}
					/>
					<Route
						path="ingest/delete"
						element={<Navigate to="/ingest/documents" replace />}
					/>
					<Route
						path="ingest/list"
						element={<Navigate to="/ingest/documents" replace />}
					/>
					<Route
						path="reprocess"
						element={<Navigate to="/ingest/documents" replace />}
					/>
					<Route
						path="delete"
						element={<Navigate to="/ingest/documents" replace />}
					/>

					<Route element={<AdminRoute />}>
						<Route path="admin" element={<AdminHomePage />} />
						<Route
							path="admin/members"
							element={<Navigate to="/admin?tab=members" replace />}
						/>
						<Route
							path="admin/accounts"
							element={<Navigate to="/admin?tab=accounts" replace />}
						/>
						<Route
							path="admin/audit-events"
							element={<Navigate to="/admin?tab=audit" replace />}
						/>
						<Route
							path="admin/knowledge-bases/:kbId/grants"
							element={<KnowledgeBaseGrantsPage />}
						/>
						<Route
							path="admin/documents/:documentId/grants"
							element={<DocumentGrantsPage />}
						/>
						<Route
							path="admin/members/:userId/grants"
							element={<MemberGrantsPage />}
						/>
					</Route>

					<Route path="*" element={<HomeRedirect />} />
				</Route>
			</Route>
		</Routes>
	);
}
