import { useParams } from "react-router-dom";
import { MemberContentReader } from "../components/MemberContentReader";
import "./MemberReaderPage.css";

export const MemberReaderPage = () => {
    const { documentId } = useParams<{documentId: string}>();

    return (
        <div className="member-reader-container">
            <header className="member-reader-header">
                <div className="title-area">
                    <h1 className="title">文档阅读</h1>
                </div>
                <div className="action-area">
                    <button className="btn-primary">AI 提问</button>
                </div>
            </header>
            
            <main className="member-reader-content">
                {documentId && <MemberContentReader documentId={documentId} />}
            </main>
        </div>
    );
};
