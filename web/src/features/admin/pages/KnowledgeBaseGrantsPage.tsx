import { useEffect, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Card, Checkbox, Select, Space, Table, Tag, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useNavigate, useParams } from "react-router-dom";
import {
	deleteKnowledgeBaseGrant,
	listKnowledgeBaseGrants,
	listMembers,
	upsertKnowledgeBaseGrant,
	type KnowledgeBaseGrant,
	type WorkspaceMember,
} from "../../../shared/api/adminApi";
import { listKnowledgeBases } from "../../../shared/api/knowledgeApi";
import { ApiErrorAlert } from "../../../shared/ui/ApiErrorAlert";

const { Title } = Typography;

const roleColorMap: Record<string, string> = {
	KB_MANAGER: "red",
	KB_CONTRIBUTOR: "blue",
	KB_READER: "green",
};

const roleOptions: Array<{ value: KnowledgeBaseGrant["role"]; label: string }> = [
	{ value: "KB_MANAGER", label: "管理者" },
	{ value: "KB_CONTRIBUTOR", label: "贡献者" },
	{ value: "KB_READER", label: "读者" },
];

export function KnowledgeBaseGrantsPage() {
	const navigate = useNavigate();
	const { kbId } = useParams<{ kbId: string }>();
	const [selectedUserIds, setSelectedUserIds] = useState<string[]>([]);
	const [rolesByUserId, setRolesByUserId] = useState<
		Record<string, KnowledgeBaseGrant["role"]>
	>({});

	const kbQuery = useQuery({
		queryKey: ["knowledge-bases"],
		queryFn: listKnowledgeBases,
		select: (data) => data.find((kb) => kb.id === kbId),
	});
	const grantsQuery = useQuery({
		queryKey: ["admin", "knowledge-base-grants", kbId],
		queryFn: () => listKnowledgeBaseGrants(kbId!),
		enabled: !!kbId,
	});
	const membersQuery = useQuery({
		queryKey: ["admin", "members"],
		queryFn: listMembers,
	});

	/* eslint-disable react-hooks/set-state-in-effect -- 服务端数据同步到编辑态 */
	useEffect(() => {
		const grants = grantsQuery.data ?? [];
		setSelectedUserIds((prev) => {
			const next = grants.map((item) => item.userId);
			if (prev.length === next.length && prev.every((v, i) => v === next[i])) return prev;
			return next;
		});
		setRolesByUserId(
			Object.fromEntries(grants.map((item) => [item.userId, item.role])),
		);
	}, [grantsQuery.data, kbId]);
	/* eslint-enable react-hooks/set-state-in-effect */

	const replaceMutation = useMutation({
		mutationFn: async () => {
			const currentGrants = grantsQuery.data ?? [];
			const nextGrantedUserIds = new Set(selectedUserIds);
			const deleteTargets = currentGrants.filter(
				(item) => !nextGrantedUserIds.has(item.userId),
			);

			await Promise.all([
				...selectedUserIds.map((userId) =>
					upsertKnowledgeBaseGrant(
						kbId!,
						userId,
						rolesByUserId[userId] ?? "KB_READER",
					),
				),
				...deleteTargets.map((item) =>
					deleteKnowledgeBaseGrant(kbId!, item.userId),
				),
			]);

			return listKnowledgeBaseGrants(kbId!);
		},
		onSuccess: (data) => {
			setSelectedUserIds(data.map((item) => item.userId));
			setRolesByUserId(
				Object.fromEntries(data.map((item) => [item.userId, item.role])),
			);
			grantsQuery.refetch();
			message.success("知识库授权已保存");
		},
	});

	const members = (membersQuery.data ?? []).filter(
		(item) => item.workspaceRole === "WORKSPACE_MEMBER",
	);
	const kbName = kbQuery.data?.name ?? kbId;
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
							setRolesByUserId((prev) => ({
								...prev,
								[userId]: prev[userId] ?? "KB_READER",
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
			render: (value: WorkspaceMember["workspaceRole"]) => <Tag>{value}</Tag>,
		},
		{
			title: "知识库角色",
			dataIndex: "userId",
			width: 180,
			render: (userId: string) => (
				<Select
					style={{ width: "100%" }}
					disabled={!selectedUserIds.includes(userId)}
					value={rolesByUserId[userId] ?? "KB_READER"}
					options={roleOptions}
					onChange={(value: KnowledgeBaseGrant["role"]) =>
						setRolesByUserId((prev) => ({ ...prev, [userId]: value }))
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
					<Tag color={roleColorMap[rolesByUserId[userId] ?? "KB_READER"]}>
						{rolesByUserId[userId] ?? "KB_READER"}
					</Tag>
				) : (
					<Tag>未授权</Tag>
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
							知识库授权管理 · {kbName}
						</Title>
						<Typography.Paragraph
							type="secondary"
							style={{ marginBottom: 0 }}
						>
							当前已授权 {selectedUserIds.length} 名成员。
						</Typography.Paragraph>
					</div>
					<Space>
						<Button
							className="console-return-button"
							onClick={() => navigate("/knowledge")}
						>
							返回知识库
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
								setRolesByUserId((prev) => ({
									...Object.fromEntries(
										members.map((item) => [
											item.userId,
											prev[item.userId] ?? "KB_READER",
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

			{kbQuery.isError && <ApiErrorAlert error={kbQuery.error} />}
			{membersQuery.isError && <ApiErrorAlert error={membersQuery.error} />}
			{grantsQuery.isError && <ApiErrorAlert error={grantsQuery.error} />}
			{replaceMutation.isError && (
				<ApiErrorAlert error={replaceMutation.error} />
			)}

			<Table<WorkspaceMember>
				rowKey="userId"
				columns={columns}
				dataSource={members}
				loading={membersQuery.isLoading || grantsQuery.isLoading}
				pagination={false}
				locale={{ emptyText: "暂无可授权成员" }}
			/>
		</Space>
	);
}
