import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { sessionSnapshot } from "../auth/session";
import { getReport, listReports, resolveReport } from "./api";
import { ApiError } from "../api/client";
import {
  InformationReportsPage,
  InformationReportDetailPage,
} from "./InformationReportPages";
vi.mock("../auth/session", () => ({
  sessionSnapshot: vi.fn(),
  subscribeSession: () => () => {},
  authenticatedFetch: vi.fn(),
}));
vi.mock("./api", async (original) => ({
  ...(await original<typeof import("./api")>()),
  listReports: vi.fn(),
  getReport: vi.fn(),
  resolveReport: vi.fn(),
}));
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "anonymous",
    member: null,
  });
});
function show(admin = false, detail = false) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter initialEntries={["/reports/test-report"]}>
        <Routes>
          <Route
            path="/reports/:id"
            element={
              detail ? (
                <InformationReportDetailPage admin={admin} />
              ) : (
                <InformationReportsPage admin={admin} />
              )
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
it("비로그인 사용자의 비공개 신고를 요청하지 않는다", () => {
  show();
  expect(screen.getByRole("link", { name: "로그인" })).toBeInTheDocument();
  expect(listReports).not.toHaveBeenCalled();
});
it("일반 회원에게 관리자 신고를 요청하지 않는다", () => {
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "member", role: "USER" },
  });
  show(true);
  expect(screen.getByRole("alert")).toHaveTextContent("관리자만");
  expect(listReports).not.toHaveBeenCalled();
});
it("내 신고 목록 상태 필터를 서버에 전달한다", async () => {
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "member", role: "USER" },
  });
  vi.mocked(listReports).mockResolvedValue({ items: [] });
  show();
  expect(
    await screen.findByText("해당하는 신고가 없습니다."),
  ).toBeInTheDocument();
  await userEvent
    .setup()
    .selectOptions(screen.getByLabelText("신고 상태"), "RESOLVED");
  expect(listReports).toHaveBeenLastCalledWith(
    false,
    "RESOLVED",
    undefined,
    expect.any(AbortSignal),
  );
});
it("관리자 처리 충돌은 대기 상태를 유지하고 최신 내용 확인을 안내한다", async () => {
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "admin", role: "ADMIN" },
  });
  vi.mocked(getReport).mockResolvedValue({
    id: "test-report",
    restaurantName: "가상 테스트 업소",
    category: "ORIGIN_CHANGED",
    state: "PENDING",
    version: 3,
    mediaIds: [],
    description: "표시 내용이 변경되었습니다.",
  });
  vi.mocked(resolveReport).mockRejectedValue(new ApiError(409));
  show(true, true);
  const user = userEvent.setup();
  await user.type(
    await screen.findByLabelText("확인 내용과 처리 사유"),
    "테스트 재확인",
  );
  await user.click(screen.getByRole("button", { name: "신고 처리 반영" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("최신 내용");
  expect(resolveReport).toHaveBeenCalledWith("test-report", {
    expectedVersion: 3,
    decision: "DISMISSED",
    reason: "테스트 재확인",
  });
  expect(screen.getByText(/원산지가 달라요 · 검토 대기/)).toBeInTheDocument();
});
