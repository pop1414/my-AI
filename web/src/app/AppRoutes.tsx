import { lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { ConsoleLayout } from "./ConsoleLayout";

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
			<Route path="/" element={<ConsoleLayout />}>
				<Route
					index
					element={<Navigate to="/ingest/upload" replace />}
				/>
				<Route path="ingest/upload" element={<IngestUploadPage />} />
				<Route path="ingest/status" element={<IngestStatusPage />} />
				<Route
					path="ingest/chunks-preview"
					element={<IngestChunksPreviewPage />}
				/>
				<Route
					path="ingest/reprocess"
					element={<IngestReprocessPage />}
				/>
				<Route path="ingest/delete" element={<IngestDeletePage />} />
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
					element={<Navigate to="/ingest/upload" replace />}
				/>
			</Route>
		</Routes>
	);
}
