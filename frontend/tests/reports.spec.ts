import { expect, test } from "@playwright/test";

test("통제된 제보 화면에서 응답 실패 후 중복 요청 키를 유지한다", async ({
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
        name: "가상 테스트 제보 업소",
        scopes: [],
      },
    }),
  );
  await page.route("**/api/v1/catalogs/ingredients", (route) =>
    route.fulfill({ json: [{ id: "test-cabbage", name: "배추" }] }),
  );
  await page.route("**/api/v1/catalogs/countries", (route) =>
    route.fulfill({
      json: [
        { code: "KR", name: "대한민국" },
        { code: "CN", name: "중국" },
      ],
    }),
  );
  await page.route("**/api/v1/media", (route) =>
    route.fulfill({ status: 201, json: { id: "test-photo" } }),
  );
  const keys: string[] = [];
  await page.route("**/api/v1/reports", (route) => {
    keys.push(route.request().headers()["idempotency-key"]!);
    expect(route.request().postDataJSON()).toMatchObject({
      usage: "SIDE_DISH",
      publicationConsent: true,
      claims: [{ classification: "DOMESTIC" }],
    });
    return route.fulfill({ status: 503, json: { code: "TEST_FAILURE" } });
  });
  await page.goto("/reports/new?restaurantId=test-restaurant");
  await page.getByLabel("메뉴 또는 제공 품목 이름").fill("테스트 반찬 김치");
  await page.getByLabel("표시판을 실제 확인한 날짜").fill("2026-09-01");
  await page.getByLabel("식재료", { exact: true }).selectOption("test-cabbage");
  await page.getByLabel("원산지 구분").selectOption("DOMESTIC");
  await page.getByLabel("표시판 원문").fill("배추: 국내산");
  await page.getByLabel("사진 선택").setInputFiles({
    name: "test-proof.png",
    mimeType: "image/png",
    buffer: Buffer.from("controlled-test-image"),
  });
  await page.getByRole("button", { name: "사진 첨부", exact: true }).click();
  await expect(page.getByText("첨부 사진 1", { exact: false })).toBeVisible();
  await page.getByRole("checkbox", { name: /공개하는 데 동의/ }).check();
  await page.getByRole("button", { name: "제보 제출", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("목록을 확인");
  await page.getByRole("button", { name: "제보 제출", exact: true }).click();
  await expect.poll(() => keys.length).toBe(2);
  expect(keys[0]).toBeTruthy();
  expect(keys[1]).toBe(keys[0]);
  await page.getByLabel("표시판 원문").fill("배추: 대한민국");
  await page.getByRole("button", { name: "제보 제출", exact: true }).click();
  await expect.poll(() => keys.length).toBe(3);
  expect(keys[2]).not.toBe(keys[1]);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    ),
  ).toBe(false);
});

test("관리자 검수 충돌은 공개 성공으로 표시하지 않는다", async ({ page }) => {
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
  await page.route("**/api/v1/catalogs/*", (route) =>
    route.fulfill({ json: [] }),
  );
  await page.route("**/api/v1/restaurants/test-restaurant", (route) =>
    route.fulfill({ json: { id: "test-restaurant", scopes: [] } }),
  );
  await page.route("**/api/v1/admin/reports/test-report", (route) =>
    route.fulfill({
      json: {
        id: "test-report",
        version: 3,
        state: "PENDING",
        submission: {
          restaurantId: "test-restaurant",
          scopeName: "테스트 반찬 김치",
          usage: "SIDE_DISH",
          observedOn: "2026-09-01",
          claims: [],
          mediaIds: [],
        },
        reviews: [],
      },
    }),
  );
  await page.route("**/api/v1/admin/reports/test-report/reviews", (route) => {
    expect(route.request().postDataJSON()).toMatchObject({
      expectedVersion: 3,
      decision: "APPROVED",
      privacyReviewedMediaIds: [],
      reason: "테스트 표시판 확인",
    });
    return route.fulfill({
      status: 409,
      json: { code: "REPORT_VERSION_CONFLICT" },
    });
  });
  await page.goto("/admin/reports/test-report");
  await page.getByLabel("검수 결과").selectOption("APPROVED");
  await page.getByLabel("검수 사유").fill("테스트 표시판 확인");
  await page.getByRole("button", { name: "검수 결과 반영" }).click();
  await expect(page.getByRole("alert")).toContainText("다른 변경이 먼저 반영");
  await expect(
    page.getByText("상태: 검수 대기", { exact: true }),
  ).toBeVisible();
});
