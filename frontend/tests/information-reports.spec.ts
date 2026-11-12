import { expect, test } from "@playwright/test";

test("정보 오류 신고는 실패 재시도 키를 유지하고 처리 결과를 본인이 확인한다", async ({
  page,
}) => {
  await page.route("**/api/v1/auth/csrf", (route) =>
    route.fulfill({ json: { token: "test-csrf", headerName: "X-CSRF-TOKEN" } }),
  );
  await page.route("**/api/v1/auth/refresh", (route) =>
    route.fulfill({
      json: { accessToken: "test-access", tokenType: "Bearer" },
    }),
  );
  await page.route("**/api/v1/members/me", (route) =>
    route.fulfill({ json: { id: "test-member", role: "USER" } }),
  );
  await page.route("**/api/v1/restaurants/test-restaurant", (route) =>
    route.fulfill({
      json: {
        id: "test-restaurant",
        name: "가상 테스트 신고 업소",
        scopes: [],
      },
    }),
  );
  const keys: string[] = [];
  await page.route("**/api/v1/information-reports", (route) => {
    keys.push(route.request().headers()["idempotency-key"]!);
    expect(route.request().postDataJSON()).toEqual({
      restaurantId: "test-restaurant",
      category: "ORIGIN_CHANGED",
      description: "표시판의 배추 원산지가 달라졌어요",
      mediaIds: [],
    });
    return keys.length === 1
      ? route.fulfill({ status: 503, json: { code: "TEST_FAILURE" } })
      : route.fulfill({ status: 201, json: { id: "test-report" } });
  });
  await page.route("**/api/v1/information-reports/test-report", (route) =>
    route.fulfill({
      json: {
        id: "test-report",
        restaurantName: "가상 테스트 신고 업소",
        category: "ORIGIN_CHANGED",
        state: "RESOLVED",
        description: "표시판의 배추 원산지가 달라졌어요",
        mediaIds: [],
        resolutionReason:
          "관리자가 변경된 표시판을 확인하고 이전 근거를 철회했습니다.",
        resolvedAt: "2026-11-12T00:00:00Z",
      },
    }),
  );
  await page.goto("/information-reports/new?restaurantId=test-restaurant");
  await page
    .getByLabel("어떤 정보가 다른가요?")
    .fill("표시판의 배추 원산지가 달라졌어요");
  await page.getByRole("button", { name: "신고 접수" }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await page.getByRole("button", { name: "신고 접수" }).click();
  await expect(page.getByRole("heading", { name: "신고 상세" })).toBeVisible();
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBeTruthy();
  expect(keys[1]).toBe(keys[0]);
  await expect(
    page.getByText(
      "관리자가 변경된 표시판을 확인하고 이전 근거를 철회했습니다.",
    ),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    ),
  ).toBe(false);
});

test("관리자는 신고 상세에서 폐업 정정을 함께 반영한다", async ({ page }) => {
  await page.route("**/api/v1/auth/csrf", (route) =>
    route.fulfill({ json: { token: "test-csrf", headerName: "X-CSRF-TOKEN" } }),
  );
  await page.route("**/api/v1/auth/refresh", (route) =>
    route.fulfill({
      json: { accessToken: "test-access", tokenType: "Bearer" },
    }),
  );
  await page.route("**/api/v1/members/me", (route) =>
    route.fulfill({ json: { id: "test-admin", role: "ADMIN" } }),
  );
  let resolved = false;
  await page.route(
    "**/api/v1/admin/information-reports/test-closure",
    (route) =>
      route.fulfill({
        json: {
          id: "test-closure",
          restaurantId: "test-shop",
          restaurantName: "테스트 가상 업소",
          category: "CLOSED",
          state: resolved ? "RESOLVED" : "PENDING",
          version: resolved ? 1 : 0,
          description: "폐업 안내를 확인했습니다.",
          mediaIds: [],
          ...(resolved ? { resolutionReason: "현장 폐업 안내 확인" } : {}),
        },
      }),
  );
  await page.route("**/api/v1/admin/restaurants/test-shop", (route) =>
    route.fulfill({
      json: {
        id: "test-shop",
        version: 2,
        snapshot: {
          name: "테스트 가상 업소",
          address: "테스트 주소",
          businessStatus: "OPEN",
        },
        corrections: [],
      },
    }),
  );
  await page.route(
    "**/api/v1/admin/information-reports/test-closure/resolution",
    (route) => {
      expect(route.request().postDataJSON()).toMatchObject({
        expectedVersion: 0,
        decision: "RESOLVED",
        reason: "현장 폐업 안내 확인",
        restaurantCorrection: { expectedVersion: 2, businessStatus: "CLOSED" },
      });
      resolved = true;
      return route.fulfill({
        json: { id: "test-closure", state: "RESOLVED", version: 1 },
      });
    },
  );
  await page.goto("/admin/information-reports/test-closure");
  await page.getByLabel("처리 결과").selectOption("RESOLVED");
  await page.getByLabel("업소 정보도 함께 정정").check();
  await page.getByLabel("영업 상태").selectOption("CLOSED");
  await page.getByLabel("확인 내용과 처리 사유").fill("현장 폐업 안내 확인");
  await page.getByRole("button", { name: "신고 처리 반영" }).click();
  await expect(
    page.getByText("현장 폐업 안내 확인", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "신고 처리 반영" }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("link", { name: "업소 정보·정정 이력" }),
  ).toHaveAttribute("href", "/admin/restaurants/test-shop");
});
