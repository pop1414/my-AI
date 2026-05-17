import { Alert } from 'antd';
import type { ReactNode } from 'react';
import { ApiError } from '../api/request';

function normalizeMessage(error: unknown): ReactNode {
  if (error instanceof ApiError) {
    return `请求失败（${error.status}）：${error.message}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '未知错误';
}

export function ApiErrorAlert({ error, style }: { error: unknown, style?: React.CSSProperties }) {
  return <Alert type="error" showIcon title={normalizeMessage(error)} style={style} />;
}
