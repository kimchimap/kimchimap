import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  use: { baseURL: "http://localhost:5173", trace: "retain-on-failure" },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    {
      name: "mobile",
      use: { ...devices["iPhone 13"], defaultBrowserType: "chromium" },
    },
  ],
  webServer: [
    {
      command: "../scripts/harness dev-backend",
      env: { SERVER_PORT: "18080" },
      url: "http://127.0.0.1:18080/api/v1/system/status",
      timeout: 120_000,
      reuseExistingServer: false,
    },
    {
      command: "pnpm dev",
      env: { API_PROXY_TARGET: "http://127.0.0.1:18080" },
      url: "http://localhost:5173",
      timeout: 30_000,
      reuseExistingServer: false,
    },
  ],
});
