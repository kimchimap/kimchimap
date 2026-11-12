import { defineConfig, devices } from "@playwright/test";
export default defineConfig({
  testDir: "./integration-tests",
  fullyParallel: false,
  workers: 1,
  forbidOnly: true,
  retries: 0,
  timeout: 60_000,
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
      command:
        "../scripts/runtime ../backend/gradlew -p ../backend browserTestServer",
      url: "http://127.0.0.1:18080/api/v1/system/status",
      timeout: 180_000,
      reuseExistingServer: false,
    },
    {
      command: "pnpm dev",
      env: {
        KIMCHIMAP_DISABLE_MAP: "1",
        API_PROXY_TARGET: "http://127.0.0.1:18080",
      },
      url: "http://localhost:5173",
      timeout: 30_000,
      reuseExistingServer: false,
    },
  ],
});
