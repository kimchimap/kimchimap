import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { sessionSnapshot } from "../auth/session";
import { ApiError } from "../api/client";
import { correctRestaurant, getAdminRestaurant } from "./admin-api";
import { RestaurantAdminPage } from "./RestaurantAdminPage";
vi.mock("../auth/session", () => ({
  sessionSnapshot: vi.fn(),
  subscribeSession: () => () => {},
  authenticatedFetch: vi.fn(),
}));
vi.mock("./admin-api", async (original) => ({
  ...(await original<typeof import("./admin-api")>()),
  correctRestaurant: vi.fn(),
  getAdminRestaurant: vi.fn(),
}));
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "admin", role: "ADMIN" },
  });
  vi.mocked(getAdminRestaurant).mockResolvedValue({
    id: "shop",
    version: 4,
    snapshot: {
      name: "테스트 가상 업소",
      address: "테스트 주소",
      businessStatus: "OPEN",
      latitude: 37.5,
      longitude: 127,
      phone: "02-123-4567",
      published: true,
    },
    corrections: [],
  });
});
function show() {
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter initialEntries={["/admin/restaurants/shop"]}>
        <Routes>
          <Route
            path="/admin/restaurants/:id"
            element={<RestaurantAdminPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
it("일반 사용자는 업소 관리 데이터를 요청하지 않는다", () => {
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "user", role: "USER" },
  });
  show();
  expect(screen.getByRole("alert")).toHaveTextContent("관리자만");
  expect(getAdminRestaurant).not.toHaveBeenCalled();
});
it("폐업 정정은 조회한 버전과 사유를 전달하고 충돌 시 입력을 보존한다", async () => {
  vi.mocked(correctRestaurant).mockRejectedValue(new ApiError(409));
  show();
  const user = userEvent.setup();
  await user.selectOptions(await screen.findByLabelText("영업 상태"), "CLOSED");
  await user.type(
    screen.getByLabelText("정정 사유와 확인 근거"),
    "현장 폐업 확인",
  );
  await user.click(screen.getByRole("button", { name: "업소 정정 반영" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("최신 내용");
  expect(correctRestaurant).toHaveBeenCalledWith(
    "shop",
    expect.objectContaining({
      expectedVersion: 4,
      businessStatus: "CLOSED",
      reason: "현장 폐업 확인",
      phone: "02-123-4567",
    }),
  );
  expect(screen.getByLabelText("정정 사유와 확인 근거")).toHaveValue(
    "현장 폐업 확인",
  );
});
it("좌표와 전화번호를 지우는 정정은 이전 값을 재전송하지 않는다", async () => {
  vi.mocked(correctRestaurant).mockRejectedValue(new ApiError(409));
  show();
  const user = userEvent.setup();
  await user.clear(await screen.findByLabelText("위도"));
  await user.clear(screen.getByLabelText("경도"));
  await user.clear(screen.getByLabelText("공개할 업소 전화번호"));
  await user.click(
    screen.getByLabelText(
      "이름·주소·위치가 같은 가게의 정보임을 확인했습니다.",
    ),
  );
  await user.type(
    screen.getByLabelText("정정 사유와 확인 근거"),
    "잘못된 위치와 연락처 제거",
  );
  await user.click(screen.getByRole("button", { name: "업소 정정 반영" }));
  await screen.findByRole("alert");
  const input = vi.mocked(correctRestaurant).mock.calls[0]![1];
  expect(input).not.toHaveProperty("latitude");
  expect(input).not.toHaveProperty("longitude");
  expect(input.phone).toBe("");
});
