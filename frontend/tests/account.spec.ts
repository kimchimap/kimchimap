import { expect, test } from "@playwright/test";

test("회원 탈퇴는 확인과 서버 응답 뒤에만 완료하고 세션을 정리한다", async ({
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
  let attempts = 0;
  await page.route("**/api/v1/members/me", (route) => {
    if (route.request().method() === "GET")
      return route.fulfill({ json: { id: "test-account", role: "USER" } });
    expect(route.request().method()).toBe("DELETE");
    expect(route.request().headers().authorization).toBe("Bearer test-access");
    attempts++;
    return attempts === 1
      ? route.fulfill({ status: 503, json: { code: "TEST_FAILURE" } })
      : route.fulfill({ status: 204 });
  });
  await page.goto("/account");
  const withdraw = page.getByRole("button", { name: "회원 탈퇴", exact: true });
  await expect(withdraw).toBeDisabled();
  await page.getByRole("checkbox").check();
  await withdraw.click();
  await expect(page.getByRole("alert")).toContainText(
    "처리 결과를 확인하지 못했습니다",
  );
  expect(attempts).toBe(1);
  await withdraw.click();
  await expect(
    page.getByRole("heading", { name: "회원 탈퇴를 완료했습니다" }),
  ).toBeVisible();
  await expect(
    page
      .getByRole("navigation", { name: "회원 메뉴" })
      .getByRole("link", { name: "로그인", exact: true }),
  ).toBeVisible();
  expect(attempts).toBe(2);
});
