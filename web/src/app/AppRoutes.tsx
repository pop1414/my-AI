import { Navigate, Route, Routes } from 'react-router-dom';
import { ConsoleLayout } from './ConsoleLayout';
import { IngestUploadPage } from '../features/ingest/pages/IngestUploadPage';
import { IngestStatusPage } from '../features/ingest/pages/IngestStatusPage';
import { IngestChunksPreviewPage } from '../features/ingest/pages/IngestChunksPreviewPage';
import { PlaceholderPage } from '../features/placeholder/pages/PlaceholderPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<ConsoleLayout />}>
        <Route index element={<Navigate to="/ingest/upload" replace />} />
        <Route path="ingest/upload" element={<IngestUploadPage />} />
        <Route path="ingest/status" element={<IngestStatusPage />} />
        <Route path="ingest/chunks-preview" element={<IngestChunksPreviewPage />} />
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
        <Route
          path="reprocess"
          element={
            <PlaceholderPage
              title="文档重处理"
              description="重处理接口尚未实现，当前页面仅保留导航占位。"
            />
          }
        />
        <Route path="*" element={<Navigate to="/ingest/upload" replace />} />
      </Route>
    </Routes>
  );
}
