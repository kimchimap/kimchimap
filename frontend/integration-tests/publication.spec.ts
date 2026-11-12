import { expect, test, type Page } from "@playwright/test";
import type { Report } from "../src/report/api";

async function login(page: Page, actor: "회원" | "관리자") {
  await page.goto("/login");
  await page.getByRole("link", { name: "카카오로 로그인" }).click();
  await page
    .getByRole("link", { name: `테스트 ${actor}`, exact: true })
    .click();
  await expect(page.getByRole("link", { name: "계정 관리" })).toBeVisible();
}

test("실제 서버에서 사진 제보 승인 후 지도 필터·좋아요·오류 신고를 연결한다", async ({
  page,
}) => {
  await login(page, "회원");
  const name = `테스트 가상 김치 가게 ${crypto.randomUUID().slice(0, 8)}`;
  await page.goto("/reports/new?latitude=37.5665&longitude=126.978");
  await page.getByLabel("가게 이름").fill(name);
  await page.getByLabel("가게 주소").fill("테스트 서울 가상 주소");
  await page.getByLabel("메뉴 또는 제공 품목 이름").fill("테스트 반찬 김치");
  await page
    .getByLabel("표시판을 실제 확인한 날짜")
    .fill(new Date().toISOString().slice(0, 10));
  await page
    .getByLabel("식재료", { exact: true })
    .selectOption("00000000-0000-4000-8000-000000000001");
  await page.getByLabel("원산지 구분").selectOption("DOMESTIC");
  await page.getByLabel("표시판 원문").fill("테스트 배추 국내산");
  await page.getByRole("button", { name: "식재료 추가", exact: true }).click();
  await page
    .getByLabel("식재료", { exact: true })
    .nth(1)
    .selectOption("00000000-0000-4000-8000-000000000002");
  await page.getByLabel("원산지 구분").nth(1).selectOption("DOMESTIC");
  await page.getByLabel("표시판 원문").nth(1).fill("테스트 고춧가루 국내산");
  const image = await page.evaluate(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 400;
    canvas.height = 100;
    const context = canvas.getContext("2d")!;
    context.fillStyle = "white";
    context.fillRect(0, 0, 400, 100);
    context.fillStyle = "black";
    context.font = "24px sans-serif";
    context.fillText("TEST / 합성 원산지 자료", 10, 50);
    return canvas.toDataURL("image/png").split(",")[1]!;
  });
  await page.getByLabel("사진 선택").setInputFiles({
    name: "test-proof.png",
    mimeType: "image/png",
    buffer: Buffer.from(image, "base64"),
  });
  await page.getByRole("button", { name: "사진 첨부", exact: true }).click();
  await expect(page.getByText("첨부 사진 1", { exact: false })).toBeVisible();
  await page.getByRole("checkbox", { name: /공개하는 데 동의/ }).check();
  const submitted = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/v1/reports") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "제보 제출", exact: true }).click();
  const submission = await submitted;
  expect(submission.status()).toBe(201);
  const report = (await submission.json()) as Report;
  const mediaId = report.submission?.mediaIds[0];
  expect(mediaId).toBeTruthy();
  expect(
    (await page.request.get(`/api/v1/media/${mediaId}/public`)).status(),
  ).toBe(404);
  await expect(page).toHaveURL(new RegExp(`/reports/${report.id}$`));
  await page.goto(`/admin/reports/${report.id}`);
  await expect(page.getByRole("alert")).toContainText("관리자만");
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(
    page
      .getByRole("navigation", { name: "회원 메뉴" })
      .getByRole("link", { name: "로그인", exact: true }),
  ).toBeVisible();
  await login(page, "관리자");
  await page.goto(`/admin/reports/${report.id}`);
  await page.getByLabel("검수 결과").selectOption("APPROVED");
  await page.getByRole("checkbox", { name: /해당 김치 품목/ }).check();
  await page.getByRole("checkbox", { name: /위치·주소·김치 표시판/ }).check();
  await page.getByRole("checkbox", { name: /사진 1의 개인정보/ }).check();
  await page.getByLabel("검수 사유").fill("테스트 합성 표시판과 위치 확인");
  await page.getByRole("button", { name: "검수 결과 반영" }).click();
  await expect(
    page.getByRole("link", { name: "등록된 가게 보기" }),
  ).toBeVisible();
  const detail = await page
    .getByRole("link", { name: "등록된 가게 보기" })
    .getAttribute("href");
  expect(detail).toMatch(/^\/restaurants\/[0-9a-f-]+$/);
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(
    page
      .getByRole("navigation", { name: "회원 메뉴" })
      .getByRole("link", { name: "로그인", exact: true }),
  ).toBeVisible();
  await login(page, "회원");
  await page.goto("/");
  for (const filter of ["국산 배추", "국산 고춧가루", "둘 다 국산"]) {
    await page.getByRole("button", { name: filter, exact: true }).click();
    await expect(
      page.getByRole("button", { name: new RegExp(name) }),
    ).toBeVisible();
  }
  expect(
    (await page.request.get(`/api/v1/media/${mediaId}/public`)).status(),
  ).toBe(200);
  await page.goto(detail!);
  await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();
  await expect(
    page.getByText("인증 날짜 (국산김치맵 제보 승인)", { exact: true }).first(),
  ).toBeVisible();
  await page.getByRole("button", { name: "좋아요 추가" }).click();
  await expect(page.getByText("좋아요 1개", { exact: true })).toBeVisible();
  await page.goto("/likes");
  await expect(
    page.getByRole("link", { name: new RegExp(name) }),
  ).toBeVisible();
  await page.goto(detail!);
  await page.getByRole("link", { name: "정보 오류 신고하기" }).click();
  await page
    .getByLabel("어떤 정보가 다른가요?")
    .fill("테스트 업소 전화번호 확인 요청");
  await page.getByLabel("신고 유형").selectOption("CONTACT");
  await page.getByRole("button", { name: "신고 접수", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "신고 상세", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("테스트 업소 전화번호 확인 요청", { exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    ),
  ).toBe(false);
});
