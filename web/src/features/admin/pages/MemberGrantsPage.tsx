import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
	Button,
	Card,
	Checkbox,
	Input,
	Select,
	Space,
	Table,
	Tabs,
	Tag,
	Typography,
	message,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
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
import { listDocuments, type DocumentListItem } from "../../../shared/api/ingestApi";
import { listKnowledgeBases, type KnowledgeBase } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

const kbRoleOptions: Array<{ label: string; value: KnowledgeBaseGrant["role"] }> = [
	{ label: "管理者", value: "KB_MANAGER" },
	{ label: "贡献者", value: "KB_CONTRIBUTOR" },
	{ label: "读者", value: "KB_READER" },
	{ label: "问答者", value: "KB_ASKER" },
];

const documentPermissionOptions: Array<{
	label: string;
	value: DocumentGrant["permission"];
}> = [
	{ label: "可读", value: "DOC_ALLOW_READ" },
	{ label: "可管理", value: "DOC_ALLOW_MANAGE" },
	{ label: "拒绝", value: "DOC_DENY" },
];

function toggleChecked(
	currentValues: string[],
	targetValue: string,
	checked: boolean,
): string[] {
	if (checked) {
		return currentValues.includes(targetValue)
			? currentValues
			: [...currentValues, targetValue];
	}
	return currentValues.filter((item) => item !== targetValue);
}

export function MemberGrantsPage() {
	const { userId = "" } = useParams<{ userId: string }>();
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const activeTab =
		searchParams.get("tab") === "documents" ? "documents" : "knowledge";

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

	const [selectedKnowledgeBaseIds, setSelectedKnowledgeBaseIds] = useState<string[]>(
		[],
	);
	const [knowledgeBaseRoles, setKnowledgeBaseRoles] = useState<
		Record<string, KnowledgeBaseGrant["role"]>
	>({});

	const [documentPage, setDocumentPage] = useState(1);
	const [documentPageSize, setDocumentPageSize] = useState(20);
	const [documentSearch, setDocumentSearch] = useState("");
	const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>([]);
	const [documentPermissions, setDocumentPermissions] = useState<
		Record<string, DocumentGrant["permission"]>
	>({});

	const documentsQuery = useQuery({
		queryKey: ["admin", "member-grant-documents", documentSearch, documentPage, documentPageSize],
		queryFn: () =>
			listDocuments({
				filename: documentSearch || undefined,
				limit: documentPageSize,
				offset: (documentPage - 1) * documentPageSize,
			}),
	});

	useEffect(() => {
		const grants = memberKnowledgeGrantsQuery.data ?? [];
		setSelectedKnowledgeBaseIds(grants.map((item) => item.kbId));
		setKnowledgeBaseRoles(
			Object.fromEntries(grants.map((item) => [item.kbId, item.role])),
		);
	}, [memberKnowledgeGrantsQuery.data, userId]);

	useEffect(() => {
		const grants = memberDocumentGrantsQuery.data ?? [];
		setSelectedDocumentIds(grants.map((item) => item.documentId));
		setDocumentPermissions(
			Object.fromEntries(
				grants.map((item) => [item.documentId, item.permission]),
			),
		);
	}, [memberDocumentGrantsQuery.data, userId]);

	const replaceKnowledgeMutation = useMutation({
		mutationFn: (assignments: Array<{ kbId: string; role: KnowledgeBaseGrant["role"] }>) =>
			replaceMemberKnowledgeBaseGrants(userId, assignments),
		onSuccess: (data) => {
			setSelectedKnowledgeBaseIds(data.map((item) => item.kbId));
			setKnowledgeBaseRoles(
				Object.fromEntries(data.map((item) => [item.kbId, item.role])),
			);
			memberKnowledgeGrantsQuery.refetch();
			message.success("成员知识库授权已保存");
		},
	});

	const replaceDocumentMutation = useMutation({
		mutationFn: (assignments: Array<{
			documentId: string;
			permission: DocumentGrant["permission"];
		}>) => replaceMemberDocumentGrants(userId, assignments),
		onSuccess: (data) => {
			setSelectedDocumentIds(data.map((item) => item.documentId));
			setDocumentPermissions(
				Object.fromEntries(
					data.map((item) => [item.documentId, item.permission]),
				),
			);
			memberDocumentGrantsQuery.refetch();
			message.success("成员文档授权已保存");
		},
	});

	const member = useMemo<WorkspaceMember | undefined>(
		() => membersQuery.data?.find((item) => item.userId === userId),
		[membersQuery.data, userId],
	);

	const activeKnowledgeBases = useMemo<KnowledgeBase[]>(
		() =>
			(knowledgeQuery.data ?? []).filter((item) => item.status === "ACTIVE"),
		[knowledgeQuery.data],
	);
	const currentDocumentItems = documentsQuery.data?.items ?? [];
	const currentDocumentIds = currentDocumentItems.map((item) => item.documentId);
	const knowledgeCheckAll =
		activeKnowledgeBases.length > 0 &&
		selectedKnowledgeBaseIds.length === activeKnowledgeBases.length;
	const knowledgeIndeterminate =
		selectedKnowledgeBaseIds.length > 0 &&
		selectedKnowledgeBaseIds.length < activeKnowledgeBases.length;
	const visibleSelectedDocumentCount = currentDocumentIds.filter((documentId) =>
		selectedDocumentIds.includes(documentId),
	).length;
	const documentCheckAll =
		currentDocumentIds.length > 0 &&
		visibleSelectedDocumentCount === currentDocumentIds.length;
	const documentIndeterminate =
		visibleSelectedDocumentCount > 0 &&
		visibleSelectedDocumentCount < currentDocumentIds.length;

	const knowledgeColumns: ColumnsType<KnowledgeBase> = [
		{
			title: "授权",
			dataIndex: "id",
			width: 80,
			render: (kbId: string) => (
				<Checkbox
					checked={selectedKnowledgeBaseIds.includes(kbId)}
					onChange={(event) => {
						if (event.target.checked) {
							setSelectedKnowledgeBaseIds((prev) =>
								toggleChecked(prev, kbId, true),
							);
							setKnowledgeBaseRoles((prev) => ({
								...prev,
								[kbId]: prev[kbId] ?? "KB_READER",
							}));
							return;
						}
						setSelectedKnowledgeBaseIds((prev) =>
							toggleChecked(prev, kbId, false),
						);
					}}
				/>
			),
		},
		{ title: "知识库名称", dataIndex: "name", width: 220 },
		{ title: "知识库 ID", dataIndex: "id", width: 220 },
		{
			title: "授权角色",
			dataIndex: "id",
			width: 180,
			render: (kbId: string) => (
				<Select
					style={{ width: "100%" }}
					disabled={!selectedKnowledgeBaseIds.includes(kbId)}
					value={knowledgeBaseRoles[kbId] ?? "KB_READER"}
					options={kbRoleOptions}
					onChange={(value: KnowledgeBaseGrant["role"]) =>
						setKnowledgeBaseRoles((prev) => ({ ...prev, [kbId]: value }))
					}
				/>
			),
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 120,
			render: (value: KnowledgeBase["status"]) => (
				<Tag color={value === "ACTIVE" ? "success" : "default"}>{value}</Tag>
			),
		},
	];

	const documentColumns: ColumnsType<DocumentListItem> = [
		{
			title: "授权",
			dataIndex: "documentId",
			width: 80,
			render: (documentId: string) => (
				<Checkbox
					checked={selectedDocumentIds.includes(documentId)}
					onChange={(event) => {
						if (event.target.checked) {
							setSelectedDocumentIds((prev) =>
								toggleChecked(prev, documentId, true),
							);
							setDocumentPermissions((prev) => ({
								...prev,
								[documentId]: prev[documentId] ?? "DOC_ALLOW_READ",
							}));
							return;
						}
						setSelectedDocumentIds((prev) =>
							toggleChecked(prev, documentId, false),
						);
					}}
				/>
			),
		},
		{ title: "文档名称", dataIndex: "filename", width: 260, ellipsis: true },
		{ title: "文档 ID", dataIndex: "documentId", width: 260, ellipsis: true },
		{ title: "知识库 ID", dataIndex: "kbId", width: 180, ellipsis: true },
		{
			title: "文档权限",
			dataIndex: "documentId",
			width: 180,
			render: (documentId: string) => (
				<Select
					style={{ width: "100%" }}
					disabled={!selectedDocumentIds.includes(documentId)}
					value={documentPermissions[documentId] ?? "DOC_ALLOW_READ"}
					options={documentPermissionOptions}
					onChange={(value: DocumentGrant["permission"]) =>
						setDocumentPermissions((prev) => ({
							...prev,
							[documentId]: value,
						}))
					}
				/>
			),
		},
	];

	return (
		<Space direction="vertical" size={16} style={{ width: "100%" }}>
			<Card>
				<Space
					style={{ width: "100%", justifyContent: "space-between" }}
					align="start"
				>
					<div>
						<Typography.Title level={4} style={{ margin: 0 }}>
							成员授权配置
						</Typography.Title>
						<Typography.Paragraph
							type="secondary"
							style={{ marginBottom: 0 }}
						>
							{member
								? `${member.displayName}（${member.username}）`
								: userId}
						</Typography.Paragraph>
					</div>
					<Button onClick={() => navigate("/admin?tab=members")}>
						返回成员管理
					</Button>
				</Space>
			</Card>

			{membersQuery.isError && <ApiErrorAlert error={membersQuery.error} />}
			{knowledgeQuery.isError && <ApiErrorAlert error={knowledgeQuery.error} />}
			{memberKnowledgeGrantsQuery.isError && (
				<ApiErrorAlert error={memberKnowledgeGrantsQuery.error} />
			)}
			{memberDocumentGrantsQuery.isError && (
				<ApiErrorAlert error={memberDocumentGrantsQuery.error} />
			)}
			{documentsQuery.isError && <ApiErrorAlert error={documentsQuery.error} />}

			<Tabs
				activeKey={activeTab}
				onChange={(key) => setSearchParams({ tab: key })}
				items={[
					{
						key: "knowledge",
						label: "知识库授权",
						children: (
							<Card
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
										保存知识库授权
									</Button>
								}
							>
								<Typography.Paragraph type="secondary">
									当前已选择 {selectedKnowledgeBaseIds.length} 个知识库。
								</Typography.Paragraph>
								<Space style={{ marginBottom: 16 }} wrap>
									<Checkbox
										checked={knowledgeCheckAll}
										indeterminate={knowledgeIndeterminate}
										onChange={(event) => {
											if (event.target.checked) {
												setSelectedKnowledgeBaseIds(
													activeKnowledgeBases.map((item) => item.id),
												);
												setKnowledgeBaseRoles((prev) => ({
													...Object.fromEntries(
														activeKnowledgeBases.map((item) => [
															item.id,
															prev[item.id] ?? "KB_READER",
														]),
													),
												}));
												return;
											}
											setSelectedKnowledgeBaseIds([]);
										}}
									>
										本页全选
									</Checkbox>
									<Button size="small" onClick={() => setSelectedKnowledgeBaseIds([])}>
										清空选择
									</Button>
								</Space>
								{replaceKnowledgeMutation.isError && (
									<ApiErrorAlert error={replaceKnowledgeMutation.error} />
								)}
								<Table
									rowKey="id"
									columns={knowledgeColumns}
									dataSource={activeKnowledgeBases}
									pagination={false}
									loading={
										knowledgeQuery.isLoading ||
										memberKnowledgeGrantsQuery.isLoading
									}
								/>
							</Card>
						),
					},
					{
						key: "documents",
						label: "文档授权",
						children: (
							<Card
								extra={
									<Space>
										<Input.Search
											allowClear
											placeholder="按文件名搜索文档"
											onSearch={(value) => {
												setDocumentSearch(value);
												setDocumentPage(1);
											}}
											style={{ width: 260 }}
										/>
										<Button
											type="primary"
											loading={replaceDocumentMutation.isPending}
											onClick={() =>
												replaceDocumentMutation.mutate(
													selectedDocumentIds.map((documentId) => ({
														documentId,
														permission:
															documentPermissions[documentId] ??
															"DOC_ALLOW_READ",
													})),
												)
											}
										>
											保存文档授权
										</Button>
									</Space>
								}
							>
								<Typography.Paragraph type="secondary">
									当前已选择 {selectedDocumentIds.length} 个文档。
								</Typography.Paragraph>
								<Space style={{ marginBottom: 16 }} wrap>
									<Checkbox
										checked={documentCheckAll}
										indeterminate={documentIndeterminate}
										onChange={(event) => {
											if (event.target.checked) {
												setSelectedDocumentIds((prev) => {
													const next = new Set(prev);
													currentDocumentIds.forEach((documentId) =>
														next.add(documentId),
													);
													return Array.from(next);
												});
												setDocumentPermissions((prev) => ({
													...prev,
													...Object.fromEntries(
														currentDocumentIds.map((documentId) => [
															documentId,
															prev[documentId] ?? "DOC_ALLOW_READ",
														]),
													),
												}));
												return;
											}
											setSelectedDocumentIds((prev) =>
												prev.filter(
													(documentId) =>
														!currentDocumentIds.includes(documentId),
												),
											);
										}}
									>
										本页全选
									</Checkbox>
									<Button
										size="small"
										onClick={() =>
											setSelectedDocumentIds((prev) =>
												prev.filter(
													(documentId) =>
														!currentDocumentIds.includes(documentId),
												),
											)
										}
									>
										清空本页
									</Button>
								</Space>
								{replaceDocumentMutation.isError && (
									<ApiErrorAlert error={replaceDocumentMutation.error} />
								)}
								<Table
									rowKey="documentId"
									columns={documentColumns}
									dataSource={documentsQuery.data?.items ?? []}
									loading={
										documentsQuery.isLoading ||
										memberDocumentGrantsQuery.isLoading
									}
									scroll={{ x: 1200 }}
									pagination={{
										current: documentPage,
										pageSize: documentPageSize,
										total: documentsQuery.data?.total ?? 0,
										showSizeChanger: true,
										onChange: (page, pageSize) => {
											setDocumentPage(page);
											setDocumentPageSize(pageSize);
										},
									}}
								/>
							</Card>
						),
					},
				]}
			/>
		</Space>
	);
}
