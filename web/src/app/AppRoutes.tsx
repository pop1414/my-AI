import { Navigate, Route, Routes } from 'react-router-dom';
import { ConsoleLayout } from './ConsoleLayout';
import { IngestUploadPage } from '../features/ingest/pages/IngestUploadPage';
import { IngestStatusPage } from '../features/ingest/pages/IngestStatusPage';
import { IngestChunksPreviewPage } from '../features/ingest/pages/IngestChunksPreviewPage';
import { IngestReprocessPage } from '../features/ingest/pages/IngestReprocessPage';
import { PlaceholderPage } from '../features/placeholder/pages/PlaceholderPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<ConsoleLayout />}>
        <Route index element={<Navigate to="/ingest/upload" replace />} />
        <Route path="ingest/upload" element={<IngestUploadPage />} />
        <Route path="ingest/status" element={<IngestStatusPage />} />
        <Route path="ingest/chunks-preview" element={<IngestChunksPreviewPage />} />
        <Route path="ingest/reprocess" element={<IngestReprocessPage />} />
        <Route path="reprocess" element={<Navigate to="/ingest/reprocess" replace />} />
        <Route
          path="knowledge"
          element={
            <PlaceholderPage
              title="知识库管理"
              description="该模块尚处于草案阶段，后端接口暂未实现。"
            />
          }
        />
        <Route
          path="qa"
          element={
            <PlaceholderPage
              title="问答控制台"
              description="该模块尚处于草案阶段，等后端 QA 接口落地后接入。"
            />
          }
        />
        <Route path="*" element={<Navigate to="/ingest/upload" replace />} />
      </Route>
    </Routes>
  );
}
