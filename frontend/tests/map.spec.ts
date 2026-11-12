import { expect, test } from "@playwright/test";

test("지도 키 없이도 품목별 필터·목록·상세와 초점 복원을 사용한다", async ({
  page,
}, testInfo) => {
  await page.route("**/api/v1/catalogs/ingredients", (route) =>
    route.fulfill({
      json: [
        { id: "test-cabbage", name: "배추" },
        { id: "test-pepper", name: "고춧가루" },
      ],
    }),
  );
  await page.route("**/api/v1/catalogs/countries", (route) =>
    route.fulfill({
      json: [
        { code: "KR", name: "대한민국" },
        { code: "CN", name: "중국" },
      ],
    }),
  );
  const requests: unknown[] = [];
  await page.route("**/api/v1/restaurants/search", (route) => {
    requests.push(route.request().postDataJSON());
    return route.fulfill({
      json: {
        items: [
          {
            id: "test-shop",
            name: "가상 테스트 지도 업소",
            address: "테스트 주소",
            latitude: 37.56,
            longitude: 126.97,
            distanceMeters: 100,
            matchedScopes: [
              {
                id: "scope",
                name: "테스트 반찬 김치",
                usage: "SIDE_DISH",
                groupIndex: 0,
              },
            ],
          },
        ],
        truncated: false,
      },
    });
  });
  await page.route("**/api/v1/restaurants/test-shop", (route) =>
    route.fulfill({
      json: {
        id: "test-shop",
        name: "가상 테스트 지도 업소",
        address: "테스트 주소",
        businessStatus: "OPEN",
        contact: {
          number: "0200000000",
          display: "02-0000-0000",
          sourceName: "테스트 출처",
        },
        scopes: [
          {
            id: "scope",
            name: "테스트 반찬 김치",
            usage: "SIDE_DISH",
            origins: [
              {
                ingredientId: "test-cabbage",
                ingredientName: "배추",
                status: "DISPUTED",
                claims: [
                  {
                    id: "claim",
                    classification: "MIXED",
                    components: [
                      { kind: "DOMESTIC", countryName: "대한민국", ratio: 0.5 },
                      {
                        kind: "IMPORTED_SPECIFIED",
                        countryName: "중국",
                        ratio: 0.5,
                      },
                    ],
                    originalExpression: "테스트 국내산·중국산 혼합",
                    evidenceKind: "SIGNBOARD_OBSERVATION",
                    sourceName: "테스트 출처",
                    observedAt: "2026-01-01T00:00:00Z",
                    observedPrecision: "DATE",
                    collectedAt: "2026-09-01T00:00:00Z",
                    freshness: "STALE",
                    approvedAt: "2026-09-03T15:00:00Z",
                    publicReference: "javascript:alert(1)",
                  },
                ],
              },
            ],
          },
        ],
        designations: [],
      },
    }),
  );
  await page.route("**/api/v1/restaurants/test-shop/likes", (route) =>
    route.fulfill({ json: { count: 12 } }),
  );
  await page.goto("/");
  await expect(page.getByText("지도 키가 설정되지 않았습니다")).toBeVisible();
  await page.getByRole("button", { name: "국산 배추", exact: true }).click();
  await page.getByRole("button", { name: "둘 다 국산", exact: true }).click();
  await expect
    .poll(() =>
      requests.find(
        (request) =>
          (request as { groups?: { ingredients?: unknown[] }[] }).groups?.[0]
            ?.ingredients?.length === 2,
      ),
    )
    .toMatchObject({
      groups: [
        {
          ingredients: [
            {
              ingredientId: "00000000-0000-4000-8000-000000000001",
              mode: "DOMESTIC",
            },
            {
              ingredientId: "00000000-0000-4000-8000-000000000002",
              mode: "DOMESTIC",
            },
          ],
        },
      ],
    });
  const item = page.getByRole("button", { name: /가상 테스트 지도 업소/ });
  await item.click();
  await expect(
    page.getByRole("heading", { name: "가상 테스트 지도 업소", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("좋아요 12개")).toBeVisible();
  await expect(page.getByText("배추 · 상충 정보 · 검토 필요")).toBeVisible();
  await expect(
    page.getByText("인증 날짜 (국산김치맵 제보 승인)"),
  ).toBeVisible();
  await expect(page.getByText("2026. 9. 4.", { exact: true })).toBeVisible();
  await expect(page.getByText("대한민국 50% · 중국 50%")).toBeVisible();
  await expect(
    page.getByText("오래된 확인 정보", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "공개 근거 확인 (새 창)" }),
  ).toHaveCount(0);
  await expect(page.getByRole("link", { name: "전화 걸기" })).toHaveAttribute(
    "href",
    "tel:0200000000",
  );
  await page.screenshot({
    path: testInfo.outputPath("map-detail.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "상세 닫기 · 목록으로" }).click();
  await expect(item).toBeFocused();
  await expect(page.getByRole("button", { name: "둘 다 국산" })).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth,
    ),
  ).toBe(false);
});

test("넓은 영역·검색 실패·지역 이동과 위치 권한 거부를 안내한다", async ({
  page,
}) => {
  await page.addInitScript(() =>
    Object.defineProperty(navigator, "geolocation", {
      value: {
        getCurrentPosition: (
          _success: unknown,
          error: (value: { code: number }) => void,
        ) => error({ code: 1 }),
      },
    }),
  );
  let fail = false;
  await page.route("**/api/v1/restaurants/search", (route) =>
    route.fulfill(
      fail
        ? { status: 503, json: {} }
        : { json: { items: [], zoomRequired: true, truncated: true } },
    ),
  );
  await page.goto("/");
  await expect(
    page.getByText(
      "검색 영역이 넓습니다. 지도를 확대하거나 반경을 줄여 주세요.",
    ),
  ).toBeVisible();
  await page.getByRole("button", { name: "내 주변 찾기" }).click();
  await expect(page.getByText(/위치 권한이 거부되었습니다/)).toBeVisible();
  fail = true;
  await page.getByLabel("기본 지역 선택").selectOption("부산");
  await expect(page.getByText(/검색 결과를 불러오지 못했습니다/)).toBeVisible();
});
