import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import { sessionSnapshot } from "../auth/session";
import { getReports, reportError } from "./api";
import { ReportsPage } from "./ReportPages";
import { ClaimEditor } from "./ClaimEditor";
vi.mock("../auth/session", () => ({
  sessionSnapshot: vi.fn(),
  subscribeSession: () => () => {},
  authenticatedFetch: vi.fn(),
}));
vi.mock("./api", async (original) => ({
  ...(await original<typeof import("./api")>()),
  getReports: vi.fn(),
}));
beforeEach(() => {
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "anonymous",
    member: null,
  });
  vi.mocked(getReports).mockReset();
});
function show(admin = false) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter>
        <ReportsPage admin={admin} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
describe("제보 접근과 원산지 입력", () => {
  it("비로그인 제보 목록을 요청하지 않는다", () => {
    show();
    expect(screen.getByRole("link", { name: "로그인" })).toBeInTheDocument();
    expect(getReports).not.toHaveBeenCalled();
  });
  it("일반 회원에게 관리자 화면을 제공하지 않는다", () => {
    vi.mocked(sessionSnapshot).mockReturnValue({
      status: "authenticated",
      member: { id: "test-member", role: "USER" },
    });
    show(true);
    expect(screen.getByRole("alert")).toHaveTextContent("관리자만");
    expect(getReports).not.toHaveBeenCalled();
  });
  it("제보 상태와 빈 결과를 구분한다", async () => {
    vi.mocked(sessionSnapshot).mockReturnValue({
      status: "authenticated",
      member: { id: "test-member", role: "USER" },
    });
    vi.mocked(getReports).mockResolvedValue({ items: [] });
    show();
    expect(
      await screen.findByText("해당하는 제보가 없습니다."),
    ).toBeInTheDocument();
    await userEvent
      .setup()
      .selectOptions(screen.getByLabelText("제보 상태"), "APPROVED");
    expect(getReports).toHaveBeenLastCalledWith(
      false,
      "APPROVED",
      undefined,
      expect.any(AbortSignal),
    );
  });
  it("미확인과 국가 미표기 수입산을 별도로 입력한다", async () => {
    const changed = vi.fn();
    render(
      <ClaimEditor
        value={{
          ingredientId: "test-ingredient",
          classification: "UNKNOWN",
          originalExpression: "확인하지 못함",
          components: [],
        }}
        ingredients={[{ id: "test-ingredient", name: "쌀" }]}
        countries={[{ code: "KR", name: "대한민국" }]}
        onChange={changed}
      />,
    );
    await userEvent
      .setup()
      .selectOptions(
        screen.getByLabelText("원산지 구분"),
        "IMPORTED_UNSPECIFIED",
      );
    expect(changed).toHaveBeenCalledWith(
      expect.objectContaining({
        classification: "IMPORTED_UNSPECIFIED",
        components: [{ kind: "IMPORTED_UNSPECIFIED" }],
      }),
    );
  });
  it("충돌과 불명확한 네트워크 결과에 재검토를 안내한다", () => {
    expect(reportError(new ApiError(409))).toContain("최신 내용");
    expect(reportError(new Error())).toContain("목록을 확인");
  });
});
