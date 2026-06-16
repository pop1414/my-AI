import {
	createContext,
	useCallback,
	useContext,
	useEffect,
	useMemo,
	useRef,
	useState,
	type ReactNode,
} from "react";
import {
	getCurrentUser,
	login as authLogin,
	logout as authLogout,
	type CurrentUserResponse,
} from "../api/authApi";
import type { ApiError } from "../api/request";

// ── 三态认证模型 ──

export type AuthStatus = "loading" | "authenticated" | "anonymous";

export interface AuthState {
	status: AuthStatus;
	user: CurrentUserResponse | null;
	isAuthenticated: boolean;
	isAdmin: boolean;
	visibleMenuKeys: string[];
	defaultLandingPath: string;
	login: (username: string, password: string) => Promise<CurrentUserResponse>;
	logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

const MENU_ORDER = [
	"/ingest/documents",
	"/ingest/upload",
	"/knowledge",
	"/qa",
	"/admin",
] as const;

function resolveVisibleMenuKeys(user: CurrentUserResponse | null): string[] {
	if (!user) {
		return [];
	}

	const keys: string[] = [];
	const { capabilities } = user;
	if (capabilities.canAccessDocumentList) keys.push("/ingest/documents");
	if (capabilities.canUploadDocument) keys.push("/ingest/upload");
	if (capabilities.canAccessKnowledge) keys.push("/knowledge");
	if (capabilities.canAskQuestion) keys.push("/qa");
	if (capabilities.canAccessAdmin) keys.push("/admin");
	return keys;
}

function resolveDefaultLandingPath(visibleMenuKeys: string[]): string {
	const firstVisible = MENU_ORDER.find((key) => visibleMenuKeys.includes(key));
	return firstVisible ?? "/no-access";
}

// ── Hook ──

// eslint-disable-next-line react-refresh/only-export-components -- Hook 与 Provider 紧密耦合，同文件导出
export function useAuth(): AuthState {
	const ctx = useContext(AuthContext);
	if (!ctx) {
		throw new Error("useAuth() must be used within an <AuthProvider>");
	}
	return ctx;
}

// ── Provider ──

export function AuthProvider({ children }: { children: ReactNode }) {
	const [status, setStatus] = useState<AuthStatus>("loading");
	const [user, setUser] = useState<CurrentUserResponse | null>(null);
	const mountedRef = useRef(true);

	// 挂载时恢复登录态
	useEffect(() => {
		mountedRef.current = true;

		getCurrentUser()
			.then((u) => {
				if (mountedRef.current) {
					setUser(u);
					setStatus("authenticated");
				}
			})
			.catch((err: unknown) => {
				if (mountedRef.current) {
					setUser(null);
					setStatus("anonymous");
					// 非 401 错误保留排障信息到 console，不阻塞页面渲染
					const apiErr = err as ApiError | undefined;
					if (apiErr?.status && apiErr.status !== 401) {
						console.error("AuthProvider: 登录态恢复失败", {
							status: apiErr.status,
							message: apiErr.message,
						});
					}
				}
			});

		return () => {
			mountedRef.current = false;
		};
	}, []);

	const login = useCallback(async (username: string, password: string) => {
		const res = await authLogin(username, password);
		setUser(res.user);
		setStatus("authenticated");
		return res.user;
	}, []);

	const logout = useCallback(async () => {
		try {
			await authLogout();
		} catch {
			// 后端返回 204 或 401 均视为登出完成，忽略异常
		}
		setUser(null);
		setStatus("anonymous");
		window.location.href = "/login";
	}, []);

	const visibleMenuKeys = useMemo(() => resolveVisibleMenuKeys(user), [user]);
	const defaultLandingPath = useMemo(
		() => resolveDefaultLandingPath(visibleMenuKeys),
		[visibleMenuKeys],
	);

	const value = useMemo<AuthState>(
		() => ({
			status,
			user,
			isAuthenticated: status === "authenticated",
			isAdmin:
				status === "authenticated" &&
				Boolean(user?.capabilities.canAccessAdmin),
			visibleMenuKeys,
			defaultLandingPath,
			login,
			logout,
		}),
		[status, user, visibleMenuKeys, defaultLandingPath, login, logout],
	);

	return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
