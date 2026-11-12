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
