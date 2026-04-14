import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
	const env = loadEnv(mode, process.cwd(), "");
	const proxyTarget = env.VITE_PROXY_TARGET || "http://localhost:8080";

	return {
		plugins: [react()],
		server: {
			host: "0.0.0.0",
			port: 3000,
			proxy: {
				"/api": {
					target: proxyTarget,
					changeOrigin: true,
				},
			},
		},
	};
});
