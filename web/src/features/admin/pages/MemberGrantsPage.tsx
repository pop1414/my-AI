import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
	Button,
	Card,
	Input,
	Space,
	Tabs,
	Typography,
	message,
} from "antd";
import { useParams, useSearchParams } from "react-router-dom";
import {
	listMemberDocumentGrants,
	listMemberKnowledgeBaseGrants,
	listMembers,
	replaceMemberDocumentGrants,
	replaceMemberKnowledgeBaseGrants,
	type DocumentGrant,
	type KnowledgeBaseGrant,
	type WorkspaceMember,
} from "../../../shared/api/adminApi";
import { listDocuments, type DocumentListPageResponse } from "../../../shared/api/ingestApi";
import { listKnowledgeBases, type KnowledgeBase } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

import { MemberPageHeader } from "../components/MemberPageHeader";
import { KnowledgeGrantTable } from "../components/KnowledgeGrantTable";
import { DocumentGrantTable } from "../components/DocumentGrantTable";

async function listDocumentsForKnowledgeBaseGrants(params: {
	kbIds: string[];
	filename?: string;
	limit: number;
	offset: number;
}): Promise<DocumentListPageResponse> {
	if (params.kbIds.length === 0) {
		return { items: [], total: 0, limit: params.limit, offset: params.offset };
	}
	const pages = await Promise.all(
		params.kbIds.map(async (kbId) => {
			const items: DocumentListPageResponse["items"] = [];
			let offset = 0;
			let total = Number.POSITIVE_INFINITY;
			while (offset < total) {
				const page = await listDocuments({
					kbId,
					filename: params.filename,
					limit: 100,
					offset,
				});
				items.push(...page.items);
				total = page.total;
				if (page.items.length === 0) break;
				offset += page.limit;
			}
			return items;
		}),
	);
	const items = pages
		.flat()
		.sort((a, b) => {
			const createdDelta = Date.parse(b.createdAt) - Date.parse(a.createdAt);
			return createdDelta === 0 ? b.documentId.localeCompare(a.documentId) : createdDelta;
		});
	const fromIndex = Math.min(params.offset, items.length);
	const toIndex = Math.min(fromIndex + params.limit, items.length);
	return {
		items: items.slice(fromIndex, toIndex),
		total: items.length,
		limit: params.limit,
		offset: params.offset,
	};
}

export function MemberGrantsPage() {
	const { userId = "" } = useParams<{ userId: string }>();
	const [searchParams, setSearchParams] = useSearchParams();
	const activeTab = searchParams.get("tab") === "documents" ? "documents" : "knowledge";

	const membersQuery = useQuery({
		queryKey: ["admin", "members"],
		queryFn: listMembers,
	});
	const knowledgeQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
	});
	const memberKnowledgeGrantsQuery = useQuery({
		queryKey: ["admin", "member-knowledge-base-grants", userId],
		queryFn: () => listMemberKnowledgeBaseGrants(userId),
		enabled: userId.length > 0,
	});
	const memberDocumentGrantsQuery = useQuery({
		queryKey: ["admin", "member-document-grants", userId],
		queryFn: () => listMemberDocumentGrants(userId),
		enabled: userId.length > 0,
	});

	const [selectedKnowledgeBaseIds, setSelectedKnowledgeBaseIds] = useState<string[]>([]);
	const [knowledgeBaseRoles, setKnowledgeBaseRoles] = useState<Record<string, KnowledgeBaseGrant["role"]>>({});

	const [documentPage, setDocumentPage] = useState(1);
	const [documentPageSize, setDocumentPageSize] = useState(20);
	const [documentSearch, setDocumentSearch] = useState("");
	const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>([]);
	const [documentPermissions, setDocumentPermissions] = useState<Record<string, DocumentGrant["permission"]>>({});
	const documentCandidateKnowledgeBaseIds = useMemo(
		() => selectedKnowledgeBaseIds.filter((kbId) => !!knowledgeBaseRoles[kbId]).sort(),
		[selectedKnowledgeBaseIds, knowledgeBaseRoles],
	);

	const documentsQuery = useQuery({
		queryKey: [
			"admin",
			"member-grant-documents",
			userId,
			documentCandidateKnowledgeBaseIds,
			documentSearch,
			documentPage,
			documentPageSize,
		],
		queryFn: () =>
			listDocumentsForKnowledgeBaseGrants({
				kbIds: documentCandidateKnowledgeBaseIds,
				filename: documentSearch || undefined,
				limit: documentPageSize,
				offset: (documentPage - 1) * documentPageSize,
			}),
	});

	/* eslint-disable react-hooks/set-state-in-effect -- 服务端数据同步到编辑态 */
	useEffect(() => {
		const grants = memberKnowledgeGrantsQuery.data ?? [];
		setSelectedKnowledgeBaseIds((prev) => {
			const next = grants.map((item) => item.kbId);
			if (prev.length === next.length && prev.every((v, i) => v === next[i])) return prev;
			return next;
		});
		setKnowledgeBaseRoles(Object.fromEntries(grants.map((item) => [item.kbId, item.role])));
	}, [memberKnowledgeGrantsQuery.data, userId]);

	useEffect(() => {
		const grants = memberDocumentGrantsQuery.data ?? [];
		setSelectedDocumentIds((prev) => {
			const next = grants.map((item) => item.documentId);
			if (prev.length === next.length && prev.every((v, i) => v === next[i])) return prev;
			return next;
		});
		setDocumentPermissions(Object.fromEntries(grants.map((item) => [item.documentId, item.permission])));
	}, [memberDocumentGrantsQuery.data, userId]);
	/* eslint-enable react-hooks/set-state-in-effect */

	const replaceKnowledgeMutation = useMutation({
		mutationFn: (assignments: Array<{ kbId: string; role: KnowledgeBaseGrant["role"] }>) =>
			replaceMemberKnowledgeBaseGrants(userId, assignments),
		onSuccess: (data) => {
			setSelectedKnowledgeBaseIds(data.map((item) => item.kbId));
			setKnowledgeBaseRoles(Object.fromEntries(data.map((item) => [item.kbId, item.role])));
			memberKnowledgeGrantsQuery.refetch();
			message.success("成员知识库授权已保存");
		},
	});

	const replaceDocumentMutation = useMutation({
		mutationFn: (assignments: Array<{ documentId: string; permission: DocumentGrant["permission"] }>) =>
			replaceMemberDocumentGrants(userId, assignments),
		onSuccess: (data) => {
			setSelectedDocumentIds(data.map((item) => item.documentId));
			setDocumentPermissions(Object.fromEntries(data.map((item) => [item.documentId, item.permission])));
			memberDocumentGrantsQuery.refetch();
			message.success("成员文档授权已保存");
		},
	});

	const member = useMemo<WorkspaceMember | undefined>(
		() => membersQuery.data?.find((item) => item.userId === userId),
		[membersQuery.data, userId],
	);

	const activeKnowledgeBases = useMemo<KnowledgeBase[]>(
		() => (knowledgeQuery.data ?? []).filter((item) => item.status === "ACTIVE"),
		[knowledgeQuery.data],
	);

	const initialDocumentGrantIds = useMemo(
		() => new Set((memberDocumentGrantsQuery.data ?? []).map((item) => item.documentId)),
		[memberDocumentGrantsQuery.data],
	);

	const newDocumentGrantCount = selectedDocumentIds.filter(
		(id) => !!documentPermissions[id] && !initialDocumentGrantIds.has(id),
	).length;
	const removedDocumentGrantCount = (memberDocumentGrantsQuery.data ?? []).filter(
		(item) => !selectedDocumentIds.includes(item.documentId),
	).length;

	return (
		<Space direction="vertical" size={16} style={{ width: "100%" }}>
			<MemberPageHeader userId={userId} member={member} title="成员授权配置" />

			{/* 错误汇总 */}
			{(membersQuery.isError || knowledgeQuery.isError || documentsQuery.isError) && (
				<ApiErrorAlert error={membersQuery.error || knowledgeQuery.error || documentsQuery.error} />
			)}

			<Tabs
				activeKey={activeTab}
				onChange={(key) => setSearchParams({ tab: key })}
				items={[
					{
						key: "knowledge",
						label: "知识库授权",
						children: (
							<Card
								title="知识库授权列表"
								extra={
									<Button
										type="primary"
										loading={replaceKnowledgeMutation.isPending}
										onClick={() =>
											replaceKnowledgeMutation.mutate(
												selectedKnowledgeBaseIds.map((kbId) => ({
													kbId,
													role: knowledgeBaseRoles[kbId] ?? "KB_READER",
												})),
											)
										}
									>
										保存更改
									</Button>
								}
							>
								<Typography.Paragraph type="secondary">
									配置成员在特定知识库中的操作角色。已选择 <Typography.Text strong>{selectedKnowledgeBaseIds.filter(id => !!knowledgeBaseRoles[id]).length}</Typography.Text> 项。
								</Typography.Paragraph>
								{replaceKnowledgeMutation.isError && (
									<ApiErrorAlert error={replaceKnowledgeMutation.error} style={{ marginBottom: 12 }} />
								)}
								<KnowledgeGrantTable
									loading={knowledgeQuery.isLoading || memberKnowledgeGrantsQuery.isLoading}
									data={activeKnowledgeBases}
									selectedIds={selectedKnowledgeBaseIds}
									roles={knowledgeBaseRoles}
									onSelectionChange={setSelectedKnowledgeBaseIds}
									onRoleChange={setKnowledgeBaseRoles}
								/>
							</Card>
						),
					},
					{
						key: "documents",
						label: "文档授权",
						children: (
							<Card
								title="文档级授权覆盖"
								extra={
									<Space>
										<Input.Search
											allowClear
											placeholder="搜索文件名"
											onSearch={(val) => { setDocumentSearch(val); setDocumentPage(1); }}
											style={{ width: 220 }}
										/>
										<Button
											type="primary"
											loading={replaceDocumentMutation.isPending}
											onClick={() =>
												replaceDocumentMutation.mutate(
													selectedDocumentIds.map((id) => ({
														documentId: id,
														permission: documentPermissions[id] ?? "DOC_ALLOW_READ",
													})),
												)
											}
										>
											保存更改
										</Button>
									</Space>
								}
							>
								<Typography.Paragraph type="secondary" data-testid="member-document-grant-summary">
									显式指定成员对特定文档的访问权限。本次新增 <Typography.Text strong>{newDocumentGrantCount}</Typography.Text> 项，待移除 <Typography.Text strong>{removedDocumentGrantCount}</Typography.Text> 项。
								</Typography.Paragraph>
								{replaceDocumentMutation.isError && (
									<ApiErrorAlert error={replaceDocumentMutation.error} style={{ marginBottom: 12 }} />
								)}
								<DocumentGrantTable
									loading={documentsQuery.isLoading || memberDocumentGrantsQuery.isLoading}
									data={documentsQuery.data?.items ?? []}
									total={documentsQuery.data?.total ?? 0}
									page={documentPage}
									pageSize={documentPageSize}
									selectedIds={selectedDocumentIds}
									permissions={documentPermissions}
									onPageChange={(p, ps) => { setDocumentPage(p); setDocumentPageSize(ps); }}
									onSelectionChange={setSelectedDocumentIds}
									onPermissionChange={setDocumentPermissions}
								/>
							</Card>
						),
					},
				]}
			/>
		</Space>
	);
}
