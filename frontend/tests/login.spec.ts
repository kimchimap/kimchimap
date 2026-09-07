import { expect, test } from "@playwright/test";

test("로그인 안내는 서버 OAuth 진입 경로와 키보드 동선을 제공한다", async ({
  page,
}) => {
  await page.goto("/login?error=failed");
  await expect(
    page.getByRole("heading", { name: "국산김치맵 로그인" }),
  ).toBeVisible();
  await expect(page.getByRole("alert")).toContainText(
    "로그인을 완료하지 못했습니다",
  );
  const login = page.getByRole("link", { name: "카카오로 로그인" });
  await expect(login).toHaveAttribute("href", "/api/v1/auth/login/kakao");
  await login.focus();
  await expect(login).toBeFocused();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    ),
  ).toBe(false);
  await page.getByRole("link", { name: "로그인 없이 둘러보기" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toContainText(
    "우리 동네 김치",
  );
});

test("통제된 인증 응답으로 복구하고 로그아웃한다", async ({ page }) => {
  let refreshes = 0;
  await page.route("**/api/v1/auth/csrf", (route) =>
    route.fulfill({ json: { token: "test-csrf", headerName: "X-CSRF-TOKEN" } }),
  );
  await page.route("**/api/v1/auth/refresh", (route) => {
    refreshes += 1;
    expect(route.request().headers()["x-csrf-token"]).toBe("test-csrf");
    return route.fulfill({
      json: { accessToken: "test-browser-token", tokenType: "Bearer" },
    });
  });
  await page.route("**/api/v1/members/me", (route) => {
    expect(route.request().headers().authorization).toBe(
      "Bearer test-browser-token",
    );
    return route.fulfill({ json: { id: "test-browser-member", role: "USER" } });
  });
  await page.route("**/api/v1/auth/logout", (route) =>
    route.fulfill({ status: 204 }),
  );
  await page.goto("/auth/complete");
  await expect(page).toHaveURL("/");
  await expect(
    page.getByRole("button", { name: "로그아웃", exact: true }),
  ).toBeVisible();
  expect(refreshes).toBe(1);
  expect(
    await page.evaluate(() => [localStorage.length, sessionStorage.length]),
  ).toEqual([0, 0]);
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "로그인", exact: true }),
  ).toBeVisible();
});
