import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import { sessionSnapshot } from "../auth/session";
import { correctOrigin, originDetail, originGroups } from "./admin-api";
import { OriginAdminPage } from "./OriginAdminPage";
vi.mock("../auth/session", () => ({
  sessionSnapshot: vi.fn(),
  subscribeSession: () => () => {},
  authenticatedFetch: vi.fn(),
}));
vi.mock("./admin-api", async (original) => ({
  ...(await original<typeof import("./admin-api")>()),
  originGroups: vi.fn(),
  originDetail: vi.fn(),
  correctOrigin: vi.fn(),
}));
beforeEach(() => {
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "test-admin", role: "ADMIN" },
  });
  vi.mocked(originGroups).mockReset();
  vi.mocked(originDetail).mockReset();
  vi.mocked(correctOrigin).mockReset();
});
function show() {
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter>
        <OriginAdminPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
it("일반 사용자에게 정정 목록을 제공하지 않는다", () => {
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "test-user", role: "USER" },
  });
  show();
  expect(screen.getByRole("alert")).toHaveTextContent("관리자만");
  expect(originGroups).not.toHaveBeenCalled();
});
it("버전 충돌 때 선택과 사유를 보존하고 최신 근거 확인을 안내한다", async () => {
  const group = {
    scopeId: "test-scope",
    ingredientId: "test-ingredient",
    restaurantName: "가상 테스트 정정 업소",
    scopeName: "반찬 김치",
    usage: "SIDE_DISH",
    ingredientName: "배추",
    status: "DISPUTED",
    version: 2,
  };
  vi.mocked(originGroups).mockResolvedValue({ items: [group] });
  vi.mocked(originDetail).mockResolvedValue({
    group,
    records: [
      {
        id: "test-record",
        classification: "DOMESTIC",
        originalExpression: "테스트 국내산 표시",
        withdrawn: false,
      },
    ],
    corrections: [],
  });
  vi.mocked(correctOrigin).mockRejectedValue(new ApiError(409));
  show();
  const user = userEvent.setup();
  await user.click(
    await screen.findByRole("button", { name: /가상 테스트 정정 업소/ }),
  );
  await user.click(
    await screen.findByRole("checkbox", { name: "이 원산지 기록 제외" }),
  );
  await user.type(
    screen.getByLabelText("정정 사유와 확인 근거"),
    "테스트 근거 재검토",
  );
  await user.click(
    screen.getByRole("button", { name: "선택 기록 철회·공개 재판정" }),
  );
  expect(correctOrigin).toHaveBeenCalledWith({
    scopeId: "test-scope",
    ingredientId: "test-ingredient",
    expectedVersion: 2,
    withdrawRecordIds: ["test-record"],
    reason: "테스트 근거 재검토",
  });
  expect(await screen.findByRole("alert")).toHaveTextContent("최신 내용");
  expect(screen.getByLabelText("정정 사유와 확인 근거")).toHaveValue(
    "테스트 근거 재검토",
  );
});
