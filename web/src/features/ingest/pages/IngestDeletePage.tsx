import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Alert, Button, Card, Form, Input, Popconfirm, Space, Tag, Typography } from 'antd';
import { z } from 'zod';
import { deleteDocument, getDocumentStatus, type DocumentStatusResponse } from '../../../shared/api/ingestApi';
import { ApiErrorAlert } from '../../../shared/ui/ApiErrorAlert';
import { ApiError } from '../../../shared/api/request';

const deleteFormSchema = z.object({
  documentId: z.string().trim().min(1, 'documentId 不能为空'),
});

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
    case 'DELETING':
      return 'warning';
    case 'DELETED':
      return 'default';
    default:
      return 'default';
  }
}

export function IngestDeletePage() {
  const [form] = Form.useForm<{ documentId: string }>();
  const [deletedDocumentId, setDeletedDocumentId] = useState<string | null>(null);
  const [statusSnapshot, setStatusSnapshot] = useState<DocumentStatusResponse | null>(null);

  const deleteMutation = useMutation({
    mutationFn: (documentId: string) => deleteDocument(documentId),
    onSuccess: async (_, documentId) => {
      setDeletedDocumentId(documentId);
      localStorage.setItem('myai:lastDocumentId', documentId);
      try {
        const latestStatus = await getDocumentStatus(documentId);
        setStatusSnapshot(latestStatus);
      } catch {
        setStatusSnapshot(null);
      }
    },
  });

  const onDelete = async () => {
    const values = deleteFormSchema.parse(form.getFieldsValue());
    await deleteMutation.mutateAsync(values.documentId);
  };

  const conflictWarning =
    deleteMutation.error instanceof ApiError && deleteMutation.error.status === 409 ? (
      <Alert
        type="warning"
        showIcon
        message="当前文档状态不允许删除（通常是 INGESTING 或 DELETING）。"
      />
    ) : null;

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title="删除文档资产"
        extra={<Typography.Text type="secondary">DELETE /api/v1/documents/{'{documentId}'}</Typography.Text>}
      >
        <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
          删除操作会清理该文档对应的源文件和全部向量版本，请谨慎操作。
        </Typography.Paragraph>

        <Form
          form={form}
          layout="inline"
          initialValues={{
            documentId: localStorage.getItem('myai:lastDocumentId') ?? '',
          }}
        >
          <Form.Item name="documentId" style={{ flex: 1, minWidth: 320 }}>
            <Input placeholder="输入 documentId" allowClear />
          </Form.Item>
          <Popconfirm
            title="确认删除该文档吗？"
            description="删除后将清理源文件与向量数据。"
            okText="确认删除"
            cancelText="取消"
            onConfirm={onDelete}
          >
            <Button danger type="primary" loading={deleteMutation.isPending}>
              删除文档
            </Button>
          </Popconfirm>
        </Form>
      </Card>

      {deleteMutation.isError && <ApiErrorAlert error={deleteMutation.error} />}
      {conflictWarning}

      {deletedDocumentId && (
        <Card title="删除结果">
          <p>
            <strong>documentId:</strong> {deletedDocumentId}
          </p>
          <p>
            <strong>HTTP:</strong> 204 No Content
          </p>
          {statusSnapshot && (
            <p>
              <strong>latest status:</strong> <Tag color={statusColor(statusSnapshot.status)}>{statusSnapshot.status}</Tag>
            </p>
          )}
        </Card>
      )}
    </Space>
  );
}
