import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";
import { sessionSnapshot } from "../auth/session";
import { getMatches, getMatch, reviewMatch } from "./api";
import { MatchesPage } from "./MatchesPage";
vi.mock("../auth/session", () => ({
  sessionSnapshot: vi.fn(),
  subscribeSession: () => () => {},
  authenticatedFetch: vi.fn(),
}));
vi.mock("./api", () => ({
  getMatches: vi.fn(),
  getMatch: vi.fn(),
  reviewMatch: vi.fn(),
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
        <MatchesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
it("관찰본 없는 과거 후보를 확정할 수 있는 것처럼 표시하지 않는다", async () => {
  vi.mocked(getMatches).mockResolvedValue([
    { sourceId: "source", externalId: "legacy", reviewable: false },
  ]);
  show();
  expect(
    await screen.findByText(/수집 관찰본이 없어 확정할 수 없습니다/),
  ).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "관찰본·후보 비교" }),
  ).not.toBeInTheDocument();
  expect(getMatch).not.toHaveBeenCalled();
});
it.each(["MATCHED", "DISTINCT"] as const)(
  "%s 결정에 관찰본 버전을 전송하고 충돌 때 입력을 보존한다",
  async (decision) => {
    vi.mocked(getMatches).mockResolvedValue([
      {
        id: "review",
        sourceId: "source",
        externalId: "external",
        reviewable: true,
      },
    ]);
    vi.mocked(getMatch).mockResolvedValue({
      id: "review",
      version: 3,
      state: "PENDING",
      name: "가상 테스트 지점",
      candidates: [
        { id: "candidate", name: "가상 테스트 후보", address: "테스트 주소" },
      ],
    });
    vi.mocked(reviewMatch).mockRejectedValue(new ApiError(409));
    show();
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: "관찰본·후보 비교" }),
    );
    const radio = await screen.findByRole("radio", {
      name: decision === "MATCHED" ? /기존 업소 연결/ : /별도 업소로 확인/,
    });
    await user.click(radio);
    await user.type(
      screen.getByLabelText("비교 근거와 결정 사유"),
      "테스트 지점 주소 비교",
    );
    await user.click(screen.getByRole("button", { name: "비교 결과 반영" }));
    expect(reviewMatch).toHaveBeenCalledWith("review", {
      expectedVersion: 3,
      decision,
      ...(decision === "MATCHED" ? { restaurantId: "candidate" } : {}),
      reason: "테스트 지점 주소 비교",
    });
    expect(await screen.findByRole("alert")).toHaveTextContent("최신 내용");
    expect(radio).toBeChecked();
    expect(screen.getByLabelText("비교 근거와 결정 사유")).toHaveValue(
      "테스트 지점 주소 비교",
    );
  },
);
it("일반 사용자에게 매칭 조회를 요청하지 않는다", () => {
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "test-user", role: "USER" },
  });
  show();
  expect(screen.getByRole("alert")).toHaveTextContent("관리자만");
  expect(getMatches).not.toHaveBeenCalled();
});
