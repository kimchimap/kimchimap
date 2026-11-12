import { expect, test } from "@playwright/test";

test("카카오 지도 SDK·타일과 로그인 인가 진입을 확인한다", async ({ page }) => {
  let tiles = 0;
  const responses = new Set<string>();
  page.on("response", (response) => {
    const host = new URL(response.url()).hostname;
    if (host !== "localhost")
      responses.add(
        `${host} ${response.request().resourceType()} ${response.status()}`,
      );
    if (
      ["mts.kakaocdn.net", "mts.daumcdn.net"].includes(host) &&
      response.request().resourceType() === "image" &&
      response.status() === 200
    )
      tiles++;
  });
  await page.goto("/");
  await expect(
    page.getByRole("button", { name: "지도 중심 위치 선택", exact: true }),
  ).toBeVisible({ timeout: 25_000 });
  try {
    await expect.poll(() => tiles, { timeout: 15_000 }).toBeGreaterThan(0);
  } catch (error) {
    console.info("외부 응답 종류", [...responses]);
    throw error;
  }
  expect(await page.locator("script#kakao-map-sdk").count()).toBe(1);
  await page
    .getByRole("button", { name: "지도 중심 위치 선택", exact: true })
    .click();
  await expect(page.getByRole("region", { name: "제보할 위치" })).toBeVisible();
  const begin = await page.request.get("/api/v1/auth/login/kakao", {
    maxRedirects: 0,
  });
  expect(begin.status()).toBe(302);
  const authorize = new URL(begin.headers().location ?? "http://invalid.local");
  expect(authorize.protocol).toBe("https:");
  expect(authorize.hostname).toBe("kauth.kakao.com");
  expect(authorize.pathname).toBe("/oauth/authorize");
  expect(authorize.searchParams.get("code_challenge_method")).toBe("S256");
  expect(authorize.searchParams.get("scope")).toBe("openid");
  const upstream = await page.request
    .get(authorize.toString(), { maxRedirects: 0 })
    .catch(() => {
      throw new Error("공식 인가 서비스 연결을 확인하지 못했습니다.");
    });
  expect(upstream.status()).toBe(302);
  const destination = new URL(
    upstream.headers().location ?? "http://invalid.local",
  );
  expect(destination.hostname).toBe("accounts.kakao.com");
});
