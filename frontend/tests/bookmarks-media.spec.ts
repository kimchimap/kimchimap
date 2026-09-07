import { expect, test } from "@playwright/test";

test("통제된 회원 화면에서 즐겨찾기와 비공개 사진 첨부를 사용한다", async ({
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
  let saved = true;
  await page.route("**/api/v1/bookmarks?*", (route) =>
    route.fulfill({
      json: {
        items: saved
          ? [
              {
                restaurantId: "test-restaurant",
                name: "가상 테스트 즐겨찾기 업소",
                address: "테스트 주소",
              },
            ]
          : [],
      },
    }),
  );
  await page.route("**/api/v1/bookmarks/test-restaurant", (route) => {
    if (route.request().method() === "DELETE") {
      saved = false;
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: { saved } });
  });
  await page.goto("/bookmarks");
  await expect(
    page.getByRole("link", { name: "가상 테스트 즐겨찾기 업소" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "즐겨찾기 해제" }).click();
  await expect(page.getByText(/아직 저장한 업소가 없습니다/)).toBeVisible();
  await page.route("**/api/v1/media", (route) => {
    expect(route.request().headers().authorization).toBe("Bearer test-access");
    expect(route.request().headers()["content-type"]).toContain(
      "multipart/form-data",
    );
    return route.fulfill({
      status: 201,
      json: {
        id: "test-media",
        contentType: "image/png",
        size: 10,
        width: 1,
        height: 1,
      },
    });
  });
  await page.goto("/media/new");
  await page.getByLabel("사진 선택").setInputFiles({
    name: "test-proof.png",
    mimeType: "image/png",
    buffer: Buffer.from("test-controlled-upload"),
  });
  await page.getByRole("button", { name: "사진 첨부" }).click();
  await expect(page.getByRole("status")).toContainText("본인과 관리자만");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    ),
  ).toBe(false);
});
