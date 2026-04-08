import { Card, Result } from 'antd';

export function PlaceholderPage({ title, description }: { title: string; description: string }) {
  return (
    <Card>
      <Result status="info" title={title} subTitle={description} />
    </Card>
  );
}
