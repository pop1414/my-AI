import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Form, Input, InputNumber, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { z } from 'zod';
import { getDocumentChunksPreview, type DocumentChunksPreviewResponse } from '../../../shared/api/ingestApi';
import { ApiErrorAlert } from '../../../shared/ui/ApiErrorAlert';

const previewFormSchema = z.object({
  documentId: z.string().trim().min(1, 'documentId 不能为空'),
  limit: z.number().int().min(1).max(200),
  previewChars: z.number().int().min(20).max(2000),
});

type QueryInput = {
  documentId: string;
  limit: number;
  previewChars: number;
};

const columns: ColumnsType<DocumentChunksPreviewResponse['chunks'][number]> = [
  { title: 'chunkIndex', dataIndex: 'chunkIndex', width: 100 },
  { title: 'contentPreview', dataIndex: 'contentPreview' },
  { title: 'sourceFile', dataIndex: 'sourceFile', width: 180 },
  { title: 'splitVersion', dataIndex: 'splitVersion', width: 120 },
  { title: 'contentHash', dataIndex: 'contentHash', width: 260 },
];

export function IngestChunksPreviewPage() {
  const [form] = Form.useForm<QueryInput>();
  const [queryInput, setQueryInput] = useState<QueryInput | null>(null);

  const previewQuery = useQuery({
    queryKey: ['ingest-chunks-preview', queryInput],
    queryFn: () => getDocumentChunksPreview(queryInput!),
    enabled: queryInput !== null,
  });

  const onSubmit = () => {
    const values = previewFormSchema.parse(form.getFieldsValue());
    setQueryInput(values);
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title="文档分块预览"
        extra={<Typography.Text type="secondary">GET /api/v1/documents/{'{documentId}'}/chunks/preview</Typography.Text>}
      >
        <Form
          form={form}
          layout="inline"
          initialValues={{
            documentId: localStorage.getItem('myai:lastDocumentId') ?? '',
            limit: 20,
            previewChars: 200,
          }}
          onFinish={onSubmit}
        >
          <Form.Item name="documentId" style={{ minWidth: 260 }}>
            <Input placeholder="documentId" allowClear />
          </Form.Item>
          <Form.Item name="limit">
            <InputNumber min={1} max={200} addonBefore="limit" />
          </Form.Item>
          <Form.Item name="previewChars">
            <InputNumber min={20} max={2000} addonBefore="previewChars" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={previewQuery.isFetching}>
            查询预览
          </Button>
        </Form>
      </Card>

      {previewQuery.isError && <ApiErrorAlert error={previewQuery.error} />}

      {previewQuery.data && (
        <Card title={`分块数量：${previewQuery.data.chunkCount}`}>
          <Table
            rowKey={(row) => `${row.chunkIndex}-${row.contentHash}`}
            columns={columns}
            dataSource={previewQuery.data.chunks}
            pagination={{ pageSize: 10 }}
            scroll={{ x: 1100 }}
          />
        </Card>
      )}
    </Space>
  );
}
