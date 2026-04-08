import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Form, Input, Space, Switch, Tag, Typography } from 'antd';
import { z } from 'zod';
import { getDocumentStatus } from '../../../shared/api/ingestApi';
import { ApiErrorAlert } from '../../../shared/ui/ApiErrorAlert';

const statusFormSchema = z.object({
  documentId: z.string().trim().min(1, 'documentId 不能为空'),
});

const terminalStatuses = new Set(['INDEXED', 'FAILED']);

function statusColor(status?: string): string {
  switch (status) {
    case 'UPLOADED':
      return 'blue';
    case 'INGESTING':
      return 'processing';
    case 'INDEXED':
      return 'success';
    case 'FAILED':
      return 'error';
    default:
      return 'default';
  }
}

export function IngestStatusPage() {
  const [form] = Form.useForm<{ documentId: string }>();
  const [targetDocumentId, setTargetDocumentId] = useState<string>(() => localStorage.getItem('myai:lastDocumentId') ?? '');
  const [autoRefresh, setAutoRefresh] = useState(true);

  const statusQuery = useQuery({
    queryKey: ['ingest-status', targetDocumentId],
    queryFn: () => getDocumentStatus(targetDocumentId),
    enabled: targetDocumentId.length > 0,
    refetchInterval: (query) => {
      if (!autoRefresh) {
        return false;
      }
      const currentStatus = query.state.data?.status;
      if (currentStatus && terminalStatuses.has(currentStatus)) {
        return false;
      }
      return 3000;
    },
  });

  const currentStatus = statusQuery.data?.status;
  const isTerminal = useMemo(() => (currentStatus ? terminalStatuses.has(currentStatus) : false), [currentStatus]);

  const onSubmit = () => {
    const values = statusFormSchema.parse(form.getFieldsValue());
    setTargetDocumentId(values.documentId);
    localStorage.setItem('myai:lastDocumentId', values.documentId);
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card title="查询文档状态" extra={<Typography.Text type="secondary">GET /api/v1/documents/{'{documentId}'}/status</Typography.Text>}>
        <Form form={form} layout="inline" initialValues={{ documentId: targetDocumentId }} onFinish={onSubmit}>
          <Form.Item name="documentId" style={{ flex: 1, minWidth: 320 }}>
            <Input placeholder="输入 documentId" allowClear />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            查询
          </Button>
        </Form>
      </Card>

      <Card
        title="轮询控制"
        extra={
          <Space>
            <span>自动刷新</span>
            <Switch checked={autoRefresh} onChange={setAutoRefresh} />
          </Space>
        }
      >
        <Space>
          <Button onClick={() => statusQuery.refetch()} loading={statusQuery.isFetching} disabled={!targetDocumentId}>
            立即刷新
          </Button>
          {isTerminal && <Tag color="success">已到达终态，自动轮询停止</Tag>}
        </Space>
      </Card>

      {statusQuery.isError && <ApiErrorAlert error={statusQuery.error} />}

      {statusQuery.data && (
        <Card title="当前状态">
          <p>
            <strong>documentId:</strong> {statusQuery.data.documentId}
          </p>
          <p>
            <strong>status:</strong> <Tag color={statusColor(statusQuery.data.status)}>{statusQuery.data.status}</Tag>
          </p>
        </Card>
      )}
    </Space>
  );
}
