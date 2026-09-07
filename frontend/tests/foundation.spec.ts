import { expect, test } from "@playwright/test";

test("공개 화면은 실제 서버에 연결하고 가상 업소를 노출하지 않는다", async ({
  page,
}, testInfo) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toContainText(
    "우리 동네 김치",
  );
  await expect(page.getByRole("status")).toContainText(
    "서버가 정상적으로 연결되었습니다.",
  );
  await expect(
    page.getByText(/아직 공개된 업소와 원산지 정보가 없습니다/),
  ).toBeVisible();
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth,
  );
  expect(overflow).toBe(false);
  await page.screenshot({
    path: testInfo.outputPath("home.png"),
    fullPage: true,
  });
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "본문으로 이동" })).toBeFocused();
});
