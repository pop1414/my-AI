import { useMemo } from "react";
import { Navigate, useSearchParams } from "react-router-dom";
import { Button, Card, Form, Input, Typography, theme } from "antd";
import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { useMutation } from "@tanstack/react-query";
import { login } from "../../../shared/api/authApi";
import { useAuth } from "../../../shared/auth/AuthContext";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";
import type { ApiError } from "../../../shared/api/request";

const { Title, Text } = Typography;

export function LoginPage() {
  const { isAuthenticated } = useAuth();
  const [searchParams] = useSearchParams();
  const { token } = theme.useToken();

  const redirect = useMemo(() => {
    const fromParam = searchParams.get("redirect");
    return fromParam || "/ingest/documents";
  }, [searchParams]);

  const mutation = useMutation({
    mutationFn: (values: { username: string; password: string }) =>
      login(values.username, values.password),
    onSuccess: () => {
      window.location.href = redirect;
    },
  });

  // 已登录用户直接跳转
  if (isAuthenticated) {
    return <Navigate to={redirect} replace />;
  }

  const apiError = mutation.error as ApiError | null;

  return (
    <div
      style={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        minHeight: "100vh",
        background: token.colorBgLayout,
      }}
    >
      <Card
        style={{
          width: 400,
          boxShadow: token.boxShadow,
        }}
        styles={{ body: { padding: 32 } }}
      >
        <div style={{ textAlign: "center", marginBottom: 32 }}>
          <Title level={2} style={{ marginBottom: 4 }}>
            my-AI
          </Title>
          <Text type="secondary">V1 · 智能文档问答平台</Text>
        </div>

        <Form
          name="login"
          size="large"
          onFinish={(values) => mutation.mutate(values)}
          autoComplete="off"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: "请输入用户名" }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="用户名"
              autoFocus
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: "请输入密码" }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
            />
          </Form.Item>

          {apiError && (
            <Form.Item>
              <ApiErrorAlert error={apiError} />
            </Form.Item>
          )}

          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              loading={mutation.isPending}
              block
            >
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
