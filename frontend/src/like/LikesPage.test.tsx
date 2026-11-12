import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { getLikeCount, getLikes, getLikeStatus, setLike } from "./api";
import { LikesPage } from "./LikesPage";
import { LikeToggle } from "./LikeToggle";
const member = {
  status: "authenticated",
  member: { id: "test-member", role: "USER" },
};
vi.mock("../auth/session", () => ({
  sessionSnapshot: () => member,
  subscribeSession: () => () => {},
}));
vi.mock("./api", () => ({
  getLikeCount: vi.fn(),
  getLikes: vi.fn(),
  getLikeStatus: vi.fn(),
  setLike: vi.fn(),
}));
beforeEach(() => {
  vi.mocked(getLikeCount).mockResolvedValue({ count: 3 });
  vi.mocked(getLikes).mockReset();
  vi.mocked(getLikeStatus).mockReset();
  vi.mocked(setLike).mockReset();
});
function show(element: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>{element}</MemoryRouter>
    </QueryClientProvider>,
  );
}
it("저장한 업소와 빈 상태를 구분해 표시한다", async () => {
  vi.mocked(getLikes).mockResolvedValue({ items: [] });
  show(<LikesPage />);
  expect(
    await screen.findByText(/아직 좋아요한 가게가 없습니다/),
  ).toBeInTheDocument();
});
it("좋아요 변경 후 서버 상태를 다시 확인한다", async () => {
  vi.mocked(getLikeStatus)
    .mockResolvedValueOnce({ saved: false })
    .mockResolvedValue({ saved: true });
  vi.mocked(setLike).mockResolvedValue();
  show(<LikeToggle restaurantId="test-restaurant" />);
  const user = userEvent.setup();
  await screen.findByRole("button", { name: "좋아요 추가" });
  await vi.waitFor(() =>
    expect(screen.getByRole("button", { name: "좋아요 추가" })).toBeEnabled(),
  );
  await user.click(screen.getByRole("button", { name: "좋아요 추가" }));
  expect(
    await screen.findByRole("button", { name: "좋아요 해제" }),
  ).toHaveAttribute("aria-pressed", "true");
  expect(await screen.findByText("좋아요 3개")).toBeInTheDocument();
  expect(setLike).toHaveBeenCalledWith("test-restaurant", true);
});
it("목록 장애를 빈 결과로 표시하지 않는다", async () => {
  vi.mocked(getLikes).mockRejectedValue(new Error("test-network"));
  show(<LikesPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "불러오지 못했습니다",
  );
  expect(
    screen.queryByText(/아직 좋아요한 가게가 없습니다/),
  ).not.toBeInTheDocument();
});
