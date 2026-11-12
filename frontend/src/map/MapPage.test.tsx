import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { MapPage } from "./MapPage";
import { searchRestaurants } from "./api";
vi.mock("./api", () => ({ searchRestaurants: vi.fn() }));
vi.mock("./MapCanvas", () => ({
  MapCanvas: ({
    onPick,
  }: {
    onPick: (point: { latitude: number; longitude: number }) => void;
  }) => (
    <button onClick={() => onPick({ latitude: 37.5665, longitude: 126.978 })}>
      테스트 위치 선택
    </button>
  ),
}));
vi.mock("../restaurant/RestaurantDetail", () => ({
  RestaurantDetail: ({ id }: { id: string }) => <h1>테스트 상세 {id}</h1>,
}));
beforeEach(() => {
  vi.mocked(searchRestaurants).mockReset();
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) =>
      Promise.resolve(
        new Response(
          JSON.stringify(
            url.endsWith("ingredients")
              ? [
                  { id: "cabbage", name: "배추" },
                  { id: "pepper", name: "고춧가루" },
                ]
              : url.endsWith("countries")
                ? [{ code: "KR", name: "대한민국" }]
                : { status: "UP" },
          ),
        ),
      ),
    ),
  );
});
afterEach(() => vi.unstubAllGlobals());
function show() {
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter>
        <MapPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
it("상세를 닫아도 필터·목록·검색 요청을 유지하고 초점을 돌려준다", async () => {
  vi.mocked(searchRestaurants).mockResolvedValue({
    items: [
      { id: "test-id", name: "가상 테스트 지도 업소", distanceMeters: 100 },
    ],
  });
  show();
  const user = userEvent.setup();
  const item = await screen.findByRole("button", {
    name: /가상 테스트 지도 업소/,
  });
  await user.click(item);
  expect(
    await screen.findByRole("heading", { name: "테스트 상세 test-id" }),
  ).toBeVisible();
  await user.click(
    screen.getByRole("button", { name: "상세 닫기 · 목록으로" }),
  );
  expect(
    screen.getByRole("button", { name: /가상 테스트 지도 업소/ }),
  ).toBeVisible();
  expect(
    screen.getByRole("button", { name: /가상 테스트 지도 업소/ }),
  ).toHaveFocus();
  expect(searchRestaurants).toHaveBeenCalledTimes(1);
});
it("지도 장애와 독립적인 지역 선택은 취소 가능한 실제 검색 계약을 사용한다", async () => {
  vi.mocked(searchRestaurants).mockResolvedValue({ items: [] });
  show();
  await screen.findByText("조건에 맞는 업소가 없습니다");
  fireEvent.change(screen.getByLabelText("기본 지역 선택"), {
    target: { value: "부산" },
  });
  await waitFor(() =>
    expect(searchRestaurants).toHaveBeenLastCalledWith(
      expect.objectContaining({
        center: { latitude: 35.1796, longitude: 129.0756 },
        radiusMeters: 3000,
      }),
      expect.any(AbortSignal),
    ),
  );
});
it("위치를 자동 요청하지 않고 권한 거부 후 지역 검색을 유지한다", async () => {
  const locate = vi.fn((_success, error) => error({ code: 1 }));
  Object.defineProperty(navigator, "geolocation", {
    configurable: true,
    value: { getCurrentPosition: locate },
  });
  vi.mocked(searchRestaurants).mockResolvedValue({ items: [] });
  show();
  expect(locate).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "내 주변 찾기" }));
  expect(await screen.findByText(/위치 권한이 거부되었습니다/)).toBeVisible();
  expect(screen.getByLabelText("기본 지역 선택")).toBeEnabled();
});

it("지역 변경 때 이전 검색을 취소하고 늦은 결과를 새 목록에 섞지 않는다", async () => {
  let finish: (
    result: Awaited<ReturnType<typeof searchRestaurants>>,
  ) => void = () => {};
  let previousSignal: AbortSignal | undefined;
  vi.mocked(searchRestaurants)
    .mockImplementationOnce((_request, signal) => {
      previousSignal = signal;
      return new Promise((resolve) => {
        finish = resolve;
      });
    })
    .mockResolvedValue({ items: [{ id: "new", name: "가상 테스트 새 지역" }] });
  show();
  await waitFor(() => expect(searchRestaurants).toHaveBeenCalledTimes(1));
  fireEvent.change(screen.getByLabelText("기본 지역 선택"), {
    target: { value: "부산" },
  });
  expect(
    await screen.findByRole("button", { name: /가상 테스트 새 지역/ }),
  ).toBeVisible();
  expect(previousSignal?.aborted).toBe(true);
  await act(async () =>
    finish({ items: [{ id: "old", name: "가상 테스트 이전 지역" }] }),
  );
  expect(screen.queryByText("가상 테스트 이전 지역")).not.toBeInTheDocument();
});

it("세 김치 필터는 같은 품목의 국내산 조건으로만 요청한다", async () => {
  vi.mocked(searchRestaurants).mockResolvedValue({ items: [] });
  show();
  await waitFor(() => expect(searchRestaurants).toHaveBeenCalled());
  const user = userEvent.setup();
  for (const [label, ids] of [
    ["국산 배추", ["00000000-0000-4000-8000-000000000001"]],
    ["국산 고춧가루", ["00000000-0000-4000-8000-000000000002"]],
    [
      "둘 다 국산",
      [
        "00000000-0000-4000-8000-000000000001",
        "00000000-0000-4000-8000-000000000002",
      ],
    ],
  ] as const) {
    await user.click(screen.getByRole("button", { name: label }));
    await waitFor(() =>
      expect(searchRestaurants).toHaveBeenLastCalledWith(
        expect.objectContaining({
          groups: [
            {
              ingredients: ids.map((ingredientId) => ({
                ingredientId,
                mode: "DOMESTIC",
                countries: [],
                includeMixed: false,
              })),
            },
          ],
        }),
        expect.any(AbortSignal),
      ),
    );
    expect(screen.getByRole("button", { name: label })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  }
});
it("지도에서 선택한 위치를 신규 제보 경로로 전달한다", async () => {
  vi.mocked(searchRestaurants).mockResolvedValue({ items: [] });
  show();
  await userEvent.click(
    screen.getByRole("button", { name: "테스트 위치 선택" }),
  );
  expect(
    screen.getByRole("link", { name: "이 위치 제보하기" }),
  ).toHaveAttribute("href", "/reports/new?latitude=37.5665&longitude=126.978");
  await userEvent.click(screen.getByRole("button", { name: "위치 선택 취소" }));
  expect(
    screen.queryByRole("link", { name: "이 위치 제보하기" }),
  ).not.toBeInTheDocument();
});
