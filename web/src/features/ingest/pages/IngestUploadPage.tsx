import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Button, Card, Form, Input, Space, Typography, Upload } from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import { InboxOutlined } from '@ant-design/icons';
import { z } from 'zod';
import { uploadDocument, type UploadResponse } from '../../../shared/api/ingestApi';
import { ApiErrorAlert } from '../../../shared/ui/ApiErrorAlert';

const uploadFormSchema = z.object({
  kbId: z.string().trim().max(128).optional(),
});

const { Dragger } = Upload;
const { Text } = Typography;

export function IngestUploadPage() {
  const [form] = Form.useForm<{ kbId?: string }>();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [result, setResult] = useState<UploadResponse | null>(null);

  const uploadMutation = useMutation({
    mutationFn: async (values: { kbId?: string; file: File }) => uploadDocument(values.file, values.kbId),
    onSuccess: (data) => {
      setResult(data);
      localStorage.setItem('myai:lastDocumentId', data.documentId);
    },
  });

  const onSubmit = async () => {
    const values = uploadFormSchema.parse(form.getFieldsValue());
    const selectedFile = fileList[0]?.originFileObj;

    if (!selectedFile) {
      form.setFields([{ name: 'kbId', errors: ['请先选择要上传的文件。'] }]);
      return;
    }

    await uploadMutation.mutateAsync({
      kbId: values.kbId,
      file: selectedFile,
    });
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card title="上传文档并触发受理" extra={<Text type="secondary">POST /api/v1/documents/upload</Text>}>
        <Form form={form} layout="vertical" initialValues={{ kbId: 'default' }} onFinish={onSubmit}>
          <Form.Item label="知识库 ID（可选）" name="kbId">
            <Input placeholder="default" />
          </Form.Item>

          <Form.Item label="文件">
            <Dragger
              multiple={false}
              maxCount={1}
              fileList={fileList}
              beforeUpload={() => false}
              onChange={(info) => setFileList(info.fileList.slice(-1))}
              onRemove={() => setFileList([])}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽文件到这里上传</p>
              <p className="ant-upload-hint">支持 txt/pdf 等后端可解析文件。</p>
            </Dragger>
          </Form.Item>

          <Button type="primary" htmlType="submit" loading={uploadMutation.isPending}>
            提交上传
          </Button>
        </Form>
      </Card>

      {uploadMutation.isError && <ApiErrorAlert error={uploadMutation.error} />}

      {result && (
        <Card title="上传结果">
          <p>
            <strong>documentId:</strong> {result.documentId}
          </p>
          <p>
            <strong>status:</strong> {result.status}
          </p>
        </Card>
      )}
    </Space>
  );
}
