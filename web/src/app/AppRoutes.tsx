import { Navigate, Route, Routes } from "react-router-dom";
import { ConsoleLayout } from "./ConsoleLayout";
import { IngestUploadPage } from "../features/ingest/pages/IngestUploadPage";
import { IngestStatusPage } from "../features/ingest/pages/IngestStatusPage";
import { IngestChunksPreviewPage } from "../features/ingest/pages/IngestChunksPreviewPage";
import { IngestReprocessPage } from "../features/ingest/pages/IngestReprocessPage";
import { IngestDeletePage } from "../features/ingest/pages/IngestDeletePage";
import { KnowledgePage } from "../features/knowledge/pages/KnowledgePage";
import { QaPage } from "../features/qa/pages/QaPage";

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
