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
      url: "http://127.0.0.1:8080/api/v1/system/status",
      timeout: 120_000,
      reuseExistingServer: false,
    },
    {
      command: "pnpm dev",
      url: "http://localhost:5173",
      timeout: 30_000,
      reuseExistingServer: false,
    },
  ],
});
