import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

function renderApp() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("공개 시작 화면", () => {
  it("공개 데이터가 없다는 안내와 실제 API 연결 상태를 표시한다", async () => {
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
      screen.getByText(/아직 공개된 업소와 원산지 정보가 없습니다/),
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
