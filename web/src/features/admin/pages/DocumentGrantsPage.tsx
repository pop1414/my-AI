import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Card, Checkbox, Select, Space, Table, Tag, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useNavigate, useParams } from "react-router-dom";
import {
	deleteDocumentGrant,
	listDocumentGrants,
	listMembers,
	upsertDocumentGrant,
	type DocumentGrant,
	type WorkspaceMember,
} from "../../../shared/api/adminApi";
import { getDocumentStatus } from "../../../shared/api/ingestApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

const { Title, Text } = Typography;

const permissionOptions: Array<{
	value: DocumentGrant["permission"];
	label: string;
}> = [
	{ value: "DOC_ALLOW_READ", label: "可读" },
	{ value: "DOC_ALLOW_MANAGE", label: "可管理" },
	{ value: "DOC_DENY", label: "拒绝" },
];

export function DocumentGrantsPage() {
	const navigate = useNavigate();
	const { documentId } = useParams<{ documentId: string }>();
	const [selectedUserIds, setSelectedUserIds] = useState<string[]>([]);
	const [permissionsByUserId, setPermissionsByUserId] = useState<
		Record<string, DocumentGrant["permission"]>
	>({});

	const docStatusQuery = useQuery({
		queryKey: ["document-status", documentId],
		queryFn: () => getDocumentStatus(documentId!),
		enabled: !!documentId,
	});

	const grantsQuery = useQuery({
		queryKey: ["admin", "document-grants", documentId],
		queryFn: () => listDocumentGrants(documentId!),
		enabled: !!documentId,
	});
	const membersQuery = useQuery({
		queryKey: ["admin", "members"],
		queryFn: listMembers,
	});

	useEffect(() => {
		const grants = grantsQuery.data ?? [];
		setSelectedUserIds(grants.map((item) => item.userId));
		setPermissionsByUserId(
			Object.fromEntries(grants.map((item) => [item.userId, item.permission])),
		);
	}, [grantsQuery.data, documentId]);

	const replaceMutation = useMutation({
		mutationFn: async () => {
			const currentGrants = grantsQuery.data ?? [];
			const currentGrantedUserIds = new Set(
				currentGrants.map((item) => item.userId),
			);
			const nextGrantedUserIds = new Set(selectedUserIds);
			const deleteTargets = currentGrants.filter(
				(item) => !nextGrantedUserIds.has(item.userId),
			);

			await Promise.all([
				...selectedUserIds.map((userId) =>
					upsertDocumentGrant(
						documentId!,
						userId,
						permissionsByUserId[userId] ?? "DOC_ALLOW_READ",
					),
				),
				...deleteTargets
					.filter((item) => currentGrantedUserIds.has(item.userId))
					.map((item) => deleteDocumentGrant(documentId!, item.userId)),
			]);

			return listDocumentGrants(documentId!);
		},
		onSuccess: (data) => {
			setSelectedUserIds(data.map((item) => item.userId));
			setPermissionsByUserId(
				Object.fromEntries(
					data.map((item) => [item.userId, item.permission]),
				),
			);
			grantsQuery.refetch();
			message.success("文档授权已保存");
		},
	});

	const members = (membersQuery.data ?? []).filter(
		(item) => item.workspaceRole === "WORKSPACE_MEMBER",
	);
	const checkAll =
		members.length > 0 && selectedUserIds.length === members.length;
	const indeterminate =
		selectedUserIds.length > 0 && selectedUserIds.length < members.length;

	const columns: ColumnsType<WorkspaceMember> = [
		{
			title: "授权",
			dataIndex: "userId",
			width: 80,
			render: (userId: string) => (
				<Checkbox
					checked={selectedUserIds.includes(userId)}
					onChange={(event) => {
						if (event.target.checked) {
							setSelectedUserIds((prev) =>
								prev.includes(userId) ? prev : [...prev, userId],
							);
							setPermissionsByUserId((prev) => ({
								...prev,
								[userId]: prev[userId] ?? "DOC_ALLOW_READ",
							}));
							return;
						}
						setSelectedUserIds((prev) =>
							prev.filter((item) => item !== userId),
						);
					}}
				/>
			),
		},
		{ title: "用户名", dataIndex: "username", width: 160 },
		{ title: "显示名", dataIndex: "displayName", width: 180 },
		{
			title: "工作区角色",
			dataIndex: "workspaceRole",
			width: 140,
			render: (value: WorkspaceMember["workspaceRole"]) => (
				<Tag className="console-pill console-pill--neutral">{value}</Tag>
			),
		},
		{
			title: "文档权限",
			dataIndex: "userId",
			width: 180,
			render: (userId: string) => (
				<Select
					className="console-permission-select"
					style={{ width: "100%" }}
					disabled={!selectedUserIds.includes(userId)}
					value={permissionsByUserId[userId] ?? "DOC_ALLOW_READ"}
					options={permissionOptions}
					onChange={(value: DocumentGrant["permission"]) =>
						setPermissionsByUserId((prev) => ({ ...prev, [userId]: value }))
					}
				/>
			),
		},
		{
			title: "当前状态",
			dataIndex: "userId",
			width: 140,
			render: (userId: string) =>
				selectedUserIds.includes(userId) ? (
					<Tag className="console-pill console-pill--blue">
						{permissionsByUserId[userId] ?? "DOC_ALLOW_READ"}
					</Tag>
				) : (
					<Tag className="console-pill console-pill--neutral">未授权</Tag>
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
						<Title level={4} style={{ margin: 0 }}>
							文档授权管理 · {docStatusQuery.data?.latestFilename || documentId}
						</Title>
						<Space direction="vertical" size={0} style={{ marginTop: 4 }}>
							<Text type="secondary" style={{ fontSize: 12, fontFamily: 'var(--console-font-mono)' }}>
								ID: {documentId}
							</Text>
							<Typography.Text
								type="secondary"
								style={{ marginBottom: 0 }}
							>
								当前已授权 {selectedUserIds.length} 名成员。
							</Typography.Text>
						</Space>
					</div>
					<Space>
						<Button
							className="console-return-button"
							onClick={() => navigate("/ingest/documents")}
						>
							返回文档列表
						</Button>
						<Button
							type="primary"
							loading={replaceMutation.isPending}
							onClick={() => replaceMutation.mutate()}
						>
							保存授权
						</Button>
					</Space>
				</Space>
			</Card>

			<Card size="small">
				<Space wrap>
					<Checkbox
						checked={checkAll}
						indeterminate={indeterminate}
						onChange={(event) => {
							if (event.target.checked) {
								setSelectedUserIds(members.map((item) => item.userId));
								setPermissionsByUserId((prev) => ({
									...Object.fromEntries(
										members.map((item) => [
											item.userId,
											prev[item.userId] ?? "DOC_ALLOW_READ",
										]),
									),
								}));
								return;
							}
							setSelectedUserIds([]);
						}}
					>
						全选成员
					</Checkbox>
					<Button size="small" onClick={() => setSelectedUserIds([])}>
						清空选择
					</Button>
					<Typography.Text type="secondary">
						当前批量选择 {selectedUserIds.length} 名成员。
					</Typography.Text>
				</Space>
			</Card>

			{docStatusQuery.isError && <ApiErrorAlert error={docStatusQuery.error} />}
			{grantsQuery.isError && <ApiErrorAlert error={grantsQuery.error} />}
			{membersQuery.isError && <ApiErrorAlert error={membersQuery.error} />}
			{replaceMutation.isError && (
				<ApiErrorAlert error={replaceMutation.error} />
			)}

			<Table<WorkspaceMember>
				rowKey="userId"
				columns={columns}
				dataSource={members}
				loading={membersQuery.isLoading || grantsQuery.isLoading || docStatusQuery.isLoading}
				pagination={false}
				locale={{ emptyText: "暂无可授权成员" }}
			/>
		</Space>
	);
}
