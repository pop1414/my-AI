import { FileSearchOutlined, FileSyncOutlined, FileTextOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons';
import { Breadcrumb, Layout, Menu, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

type MenuItem = Required<MenuProps>['items'][number];

const menuItems: MenuItem[] = [
  { key: '/ingest/upload', icon: <UploadOutlined />, label: '文档上传' },
  { key: '/ingest/status', icon: <FileSyncOutlined />, label: '状态查询' },
  { key: '/ingest/chunks-preview', icon: <FileSearchOutlined />, label: '分块预览' },
  { type: 'divider' },
  { key: '/knowledge', icon: <FileTextOutlined />, label: '知识库（草案）' },
  { key: '/qa', icon: <SearchOutlined />, label: '问答（草案）' },
  { key: '/reprocess', icon: <FileSyncOutlined />, label: '重处理（草案）' },
];

function resolveTitle(pathname: string): string {
  const map: Record<string, string> = {
    '/ingest/upload': '文档上传受理',
    '/ingest/status': '文档状态查询',
    '/ingest/chunks-preview': '文档分块预览',
    '/knowledge': '知识库管理（草案）',
    '/qa': '问答控制台（草案）',
    '/reprocess': '文档重处理（草案）',
  };
  return map[pathname] ?? 'Ingest 控制台';
}

export function ConsoleLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <Layout className="console-root">
      <Sider width={250} breakpoint="lg" collapsedWidth="0">
        <div className="console-logo">my-AI / Web Console</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header className="console-header">
          <div>
            <Title level={4} style={{ margin: 0 }}>
              {resolveTitle(location.pathname)}
            </Title>
            <Text type="secondary">当前版本聚焦 ingest 流程验证</Text>
          </div>
        </Header>
        <Content className="console-content">
          <Breadcrumb
            items={[
              { title: '控制台' },
              { title: resolveTitle(location.pathname) },
            ]}
            style={{ marginBottom: 16 }}
          />
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
