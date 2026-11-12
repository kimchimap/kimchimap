import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  define: {
    "import.meta.env.VITE_KAKAO_JAVASCRIPT_KEY": JSON.stringify(
      mode === "test" || process.env.KIMCHIMAP_DISABLE_MAP === "1"
        ? ""
        : (process.env.KAKAO_JAVASCRIPT_KEY ?? ""),
    ),
  },
  server: {
    proxy: { "/api": process.env.API_PROXY_TARGET ?? "http://127.0.0.1:8080" },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    clearMocks: true,
    restoreMocks: true,
  },
}));
