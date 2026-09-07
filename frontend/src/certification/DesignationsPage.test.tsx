import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError, getRestaurant } from "../api/client";
import { sessionSnapshot } from "../auth/session";
import {
  getDesignation,
  getDesignations,
  getDesignationSources,
  saveDesignation,
} from "./api";
import { DesignationsPage } from "./DesignationsPage";
vi.mock("../auth/session", () => ({
  sessionSnapshot: vi.fn(),
  subscribeSession: () => () => {},
  authenticatedFetch: vi.fn(),
}));
vi.mock("../api/client", async (original) => ({
  ...(await original<typeof import("../api/client")>()),
  getRestaurant: vi.fn(),
}));
vi.mock("./api", () => ({
  getDesignation: vi.fn(),
  getDesignations: vi.fn(),
  getDesignationSources: vi.fn(),
  saveDesignation: vi.fn(),
}));
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "test-admin", role: "ADMIN" },
  });
});
function show() {
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter>
        <DesignationsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
it("이용 허가가 없으면 소스와 등록 양식을 만들지 않는다", async () => {
  vi.mocked(getDesignationSources).mockResolvedValue([]);
  vi.mocked(getDesignations).mockResolvedValue([]);
  show();
  expect(await screen.findByText(/협회 이용 허가는 대기 중/)).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "지정 정보 저장" }),
  ).not.toBeInTheDocument();
  expect(saveDesignation).not.toHaveBeenCalled();
});
it("수정 시 식별자를 보존하고 취소일·사유와 기존 버전을 저장 함수에 전달한다", async () => {
  const existing = {
    id: "designation",
    version: 2,
    designation: {
      restaurantId: "restaurant",
      sourceId: "source",
      externalId: "test-external",
      schemeName: "테스트 지정",
      applicableItems: "반찬 김치",
      criteriaOriginal: "테스트 제도 기준",
      publiclyVisible: true,
    },
  };
  vi.mocked(getDesignationSources).mockResolvedValue([
    { id: "source", name: "가상 허가 소스" },
  ]);
  vi.mocked(getDesignations).mockResolvedValue([
    {
      id: "designation",
      restaurantName: "가상 테스트 업소",
      schemeName: "테스트 지정",
    },
  ]);
  vi.mocked(getDesignation).mockResolvedValue(existing);
  vi.mocked(getRestaurant).mockResolvedValue({
    id: "restaurant",
    name: "가상 테스트 업소",
  });
  vi.mocked(saveDesignation).mockRejectedValue(new ApiError(409));
  show();
  const user = userEvent.setup();
  await user.click(
    await screen.findByRole("button", { name: /가상 테스트 업소/ }),
  );
  const reason = await screen.findByLabelText("확인·변경 사유");
  expect(screen.getByLabelText("원천 지정 식별자")).toHaveAttribute("readonly");
  expect(screen.getByLabelText("허가된 출처")).toBeDisabled();
  await user.type(screen.getByLabelText("취소일"), "2026-09-08");
  await user.type(reason, "테스트 지정 취소 확인");
  await user.click(screen.getByRole("button", { name: "지정 정보 저장" }));
  expect(saveDesignation).toHaveBeenCalledWith(
    { ...existing.designation, cancelledOn: "2026-09-08" },
    "테스트 지정 취소 확인",
    existing,
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("최신 변경 이력");
  expect(reason).toHaveValue("테스트 지정 취소 확인");
});
