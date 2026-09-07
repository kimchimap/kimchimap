import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

function renderApp(path = "/") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("공개 시작 화면", () => {
  it("업소 연락처 경로에서 실제 API 형식의 번호와 전화 링크를 표시한다", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith("/api/v1/auth/")
          ? new Response("", { status: 401 })
          : new Response(
              JSON.stringify({
                id: "test-restaurant",
                name: "가상 테스트 업소",
                address: "테스트 주소",
                contact: {
                  display: "02-0000-0000",
                  number: "0200000000",
                  sourceName: "테스트 출처",
                },
              }),
            ),
      ),
    );
    vi.stubGlobal("fetch", fetchMock);
    renderApp("/restaurants/test-restaurant/contact");
    expect(
      await screen.findByRole("heading", { name: "가상 테스트 업소" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "전화 걸기" })).toHaveAttribute(
      "href",
      "tel:0200000000",
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/restaurants/test-restaurant",
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
  });

  it("공개되지 않은 업소의 연락처에 전화 링크를 표시하지 않는다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("", { status: 404 })),
    );
    renderApp("/restaurants/test-restaurant/contact");
    expect(
      await screen.findByText("공개된 업소 정보를 찾을 수 없습니다."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "전화 걸기" }),
    ).not.toBeInTheDocument();
  });
  it("원산지 데이터가 없다는 안내와 실제 API 연결 상태를 표시한다", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ serviceName: "국산김치맵", status: "UP" }),
          ),
        ),
    );
    renderApp();
    expect(
      screen.getByText(/원산지 정보는 아직 확보하지 못했습니다/),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("서버가 정상적으로 연결되었습니다."),
    ).toBeInTheDocument();
  });

  it("연결 실패를 성공으로 표시하지 않고 다시 시도할 수 있다", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("network")));
    renderApp();
    expect(
      await screen.findByText("서버 연결을 확인하지 못했습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });
});
