import { useQuery } from "@tanstack/react-query";
import { getDocumentContent } from "../../../shared/api/ingestApi";
import { Spin } from "antd";
import { LoadingOutlined } from "@ant-design/icons";

interface MemberContentReaderProps {
    documentId: string;
}

export const MemberContentReader = ({ documentId }: MemberContentReaderProps) => {
    // 假设成员默认阅读最新版本 (versionNumber: 0 或通过逻辑获取)
    // 根据 IngestDocumentVersionReadPage，获取内容需要 documentId 和 versionNumber
    const contentQuery = useQuery({
        queryKey: ["document-content", documentId, "latest"],
        queryFn: () => getDocumentContent({
            documentId,
            source: "LATEST"
        }),
        enabled: documentId.length > 0,
    });

    if (contentQuery.isLoading) {
        return (
            <div style={{ padding: '80px', textAlign: 'center' }}>
                <Spin indicator={<LoadingOutlined style={{ fontSize: 24, color: '#3ecf8e' }} spin />} />
            </div>
        );
    }

    if (contentQuery.isError) {
        return <div style={{ padding: '32px', color: '#cf2d56' }}>加载内容失败。</div>;
    }

    // 简单渲染，严格限制 HTML 标签，不展示 chunk 标识
    return (
        <article className="editorial-canvas">
            <div 
                className="body-text"
                dangerouslySetInnerHTML={{ 
                    __html: contentQuery.data?.contentMarkdown
                        .replace(/\n/g, '<br/>')
                        .replace(/# (.*?)<br\/>/g, '<h1>$1</h1>')
                        .replace(/## (.*?)<br\/>/g, '<h2>$1</h2>')
                        ?? "" 
                }} 
            />
        </article>
    );
};
