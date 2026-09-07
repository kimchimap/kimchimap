import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { sessionSnapshot } from "../auth/session";
import { readAdmin, requestRun } from "./api";
import { IngestionPage } from "./IngestionPage";
vi.mock("../auth/session", () => ({
  sessionSnapshot: vi.fn(),
  subscribeSession: () => () => {},
}));
vi.mock("./api", async (original) => ({
  ...(await original<typeof import("./api")>()),
  readAdmin: vi.fn(),
  requestRun: vi.fn(),
}));
beforeEach(() => {
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "test-admin", role: "ADMIN" },
  });
  vi.mocked(readAdmin).mockReset();
  vi.mocked(requestRun).mockReset();
});
function show() {
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter>
        <IngestionPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
it("일반 회원은 수집 관리 조회를 하지 않는다", () => {
  vi.mocked(sessionSnapshot).mockReturnValue({
    status: "authenticated",
    member: { id: "test-user", role: "USER" },
  });
  show();
  expect(screen.getByRole("alert")).toHaveTextContent("관리자만");
  expect(readAdmin).not.toHaveBeenCalled();
});
it("인증키 없는 소스의 실행을 차단하고 일부 처리 상태를 표시한다", async () => {
  vi.mocked(readAdmin).mockImplementation(async (path) =>
    path === "sources"
      ? [
          {
            id: "test-source",
            name: "테스트 소스",
            domesticQualificationSupported: true,
            collectionAllowed: true,
            republicationAllowed: true,
            credentialConfigured: false,
          },
        ]
      : [{ id: "test-job", status: "PARTIAL", readCount: 100 }],
  );
  show();
  expect(
    await screen.findByRole("button", { name: "수집 실행 요청" }),
  ).toBeDisabled();
  expect(
    await screen.findByRole("button", { name: /일부 처리/ }),
  ).toBeVisible();
});
it("재실행은 사유와 제한 페이지 수를 전달하고 접수 상태를 알린다", async () => {
  vi.mocked(readAdmin).mockImplementation(async (path) =>
    path === "sources"
      ? [
          {
            id: "test-source",
            name: "테스트 소스",
            domesticQualificationSupported: true,
            collectionAllowed: true,
            republicationAllowed: true,
            credentialConfigured: true,
          },
        ]
      : [],
  );
  vi.mocked(requestRun).mockResolvedValue({ id: "test-job", status: "QUEUED" });
  show();
  const user = userEvent.setup();
  await user.type(
    await screen.findByLabelText("재실행 사유"),
    "테스트 수집 재개",
  );
  await user.click(screen.getByRole("button", { name: "수집 실행 요청" }));
  expect(requestRun).toHaveBeenCalledWith(
    "test-source",
    { mode: "INCREMENTAL", pageBudget: 2, reason: "테스트 수집 재개" },
    expect.any(String),
  );
  expect(await screen.findByRole("status")).toHaveTextContent("접수했습니다");
});

it("원산지 없는 소스는 키와 이용 허가가 있어도 재실행을 제공하지 않는다", async () => {
  vi.mocked(readAdmin).mockImplementation(async (path) =>
    path === "sources"
      ? [
          {
            id: "test-originless",
            name: "테스트 일반음식점",
            collectionAllowed: true,
            republicationAllowed: true,
            credentialConfigured: true,
            domesticQualificationSupported: false,
          },
        ]
      : [],
  );
  show();
  expect(
    await screen.findByText(/국내산 사용 여부를 선별할 수 없는 소스/),
  ).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "수집 실행 요청" }),
  ).not.toBeInTheDocument();
  expect(requestRun).not.toHaveBeenCalled();
});
